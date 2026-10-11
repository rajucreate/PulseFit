package com.pulsefit.payment.service;

import com.pulsefit.payment.client.Clients.MemberView;
import com.pulsefit.payment.client.Clients.NotificationClient;
import com.pulsefit.payment.client.Clients.NotificationRequest;
import com.pulsefit.payment.client.Clients.PlanClient;
import com.pulsefit.payment.client.Clients.PlanView;
import com.pulsefit.payment.client.Clients.SubscriptionClient;
import com.pulsefit.payment.client.Clients.CheckoutCommand;
import com.pulsefit.payment.client.Clients.SubscriptionView;
import com.pulsefit.payment.razorpay.RazorpayGateway;
import com.pulsefit.payment.razorpay.RazorpayHttpGateway;
import com.pulsefit.payment.dto.CheckoutDtos.CancelRequest;
import com.pulsefit.payment.dto.CheckoutDtos.CheckoutResponse;
import com.pulsefit.payment.dto.CheckoutDtos.VerifyRequest;
import com.pulsefit.payment.dto.PaymentRequest;
import com.pulsefit.payment.dto.PaymentResponse;
import com.pulsefit.payment.exception.ConflictException;
import com.pulsefit.payment.exception.ResourceNotFoundException;
import com.pulsefit.payment.exception.ServiceUnavailableException;
import com.pulsefit.payment.model.Payment;
import com.pulsefit.payment.model.PaymentStatus;
import com.pulsefit.payment.repository.PaymentRepository;
import feign.FeignException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
  private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
  private static final Set<String> METHODS = Set.of("SIMULATED_CARD", "SIMULATED_UPI", "SIMULATED_FAIL");
  private static final String SIMULATED_NOTE =
      "Simulated provider only. No card was charged and no real money moved.";
  private static final String TEST_NOTE =
      "Razorpay test mode. This is not a live UPI transfer and no real money has moved.";

  private final PaymentRepository repository;
  private final SubscriptionClient subscriptions;
  private final PlanClient plans;
  private final com.pulsefit.payment.client.Clients.MemberClient members;
  private final NotificationClient notifications;
  private final RazorpayGateway razorpay;

  public PaymentService(
      PaymentRepository repository,
      SubscriptionClient subscriptions,
      PlanClient plans,
      com.pulsefit.payment.client.Clients.MemberClient members,
      NotificationClient notifications,
      RazorpayGateway razorpay) {
    this.repository = repository;
    this.subscriptions = subscriptions;
    this.plans = plans;
    this.members = members;
    this.notifications = notifications;
    this.razorpay = razorpay;
  }

  @Transactional
  public CheckoutResponse checkout(Long memberId, Long planId) {
    SubscriptionView subscription = prepare(memberId, planId);
    if (!razorpay.configured()) {
      throw new ServiceUnavailableException(RazorpayHttpGateway.missingKeys());
    }
    PlanView plan = loadPlan(subscription.planId());
    int paise = paise(plan.price());
    var open =
        repository.findFirstBySubscriptionIdAndPaymentStatus(subscription.id(), PaymentStatus.PENDING);
    if (open.isPresent() && open.get().getProviderOrderId() != null) {
      return checkoutResponse(open.get(), plan, subscription);
    }
    var order = razorpay.createOrder(paise, "sub_" + subscription.id());
    if (order.amountPaise() != paise || order.id() == null || order.id().isBlank()) {
      throw new IllegalArgumentException("Razorpay order amount did not match the plan price");
    }
    Payment payment =
        new Payment(
            memberId,
            subscription.id(),
            plan.price(),
            "RAZORPAY_UPI",
            order.id(),
            PaymentStatus.PENDING,
            OffsetDateTime.now(),
            "rzp-" + order.id(),
            false,
            TEST_NOTE);
    payment.attachOrder(order.id());
    return checkoutResponse(repository.save(payment), plan, subscription);
  }

  @Transactional
  public PaymentResponse verify(Long memberId, VerifyRequest request) {
    Payment payment =
        repository
            .findByProviderOrderId(request.razorpayOrderId())
            .orElseThrow(() -> new ResourceNotFoundException("Payment order not found"));
    if (!payment.getMemberId().equals(memberId)) {
      throw new IllegalArgumentException("This payment does not belong to the signed-in member");
    }
    if (payment.getPaymentStatus() == PaymentStatus.SUCCESS) {
      return toResponse(payment);
    }
    if (!razorpay.signatureValid(
        request.razorpayOrderId(), request.razorpayPaymentId(), request.razorpaySignature())) {
      throw new IllegalArgumentException("Payment signature is invalid");
    }
    var fetched = razorpay.fetch(request.razorpayPaymentId());
    if (!request.razorpayOrderId().equals(fetched.orderId()) || fetched.amountPaise() != paise(payment.getAmount())) {
      throw new IllegalArgumentException("Payment does not match this order");
    }
    if (!"captured".equalsIgnoreCase(fetched.status())) {
      payment.applyResult(PaymentStatus.FAILED, payment.getTransactionId(), TEST_NOTE + " Payment was not captured.");
      Payment saved = repository.save(payment);
      cancelQuietly(saved.getSubscriptionId());
      notifyQuietly(saved, "membership");
      return toResponse(saved);
    }
    payment.applyResult(PaymentStatus.SUCCESS, fetched.id(), TEST_NOTE + " Signature and amount were verified.");
    Payment saved = repository.save(payment);
    activateRequired(saved.getSubscriptionId());
    notifyQuietly(saved, "membership");
    return toResponse(saved);
  }

  @Transactional
  public PaymentResponse cancelCheckout(Long memberId, CancelRequest request) {
    Payment payment =
        repository
            .findByProviderOrderId(request.orderId())
            .orElseThrow(() -> new ResourceNotFoundException("Payment order not found"));
    if (!payment.getMemberId().equals(memberId)) {
      throw new IllegalArgumentException("This payment does not belong to the signed-in member");
    }
    if (payment.getPaymentStatus() == PaymentStatus.SUCCESS) {
      return toResponse(payment);
    }
    if (payment.getPaymentStatus() == PaymentStatus.FAILED || payment.getPaymentStatus() == PaymentStatus.REFUNDED) {
      return toResponse(payment);
    }
    payment.applyResult(PaymentStatus.FAILED, payment.getTransactionId(), TEST_NOTE + " Checkout was cancelled.");
    Payment saved = repository.save(payment);
    cancelQuietly(saved.getSubscriptionId());
    return toResponse(saved);
  }

  @Transactional
  public PaymentResponse initiate(PaymentRequest request) {
    String method = request.paymentMethod().trim().toUpperCase();
    if (!METHODS.contains(method)) {
      throw new IllegalArgumentException(
          "paymentMethod must be SIMULATED_CARD, SIMULATED_UPI, or SIMULATED_FAIL");
    }
    String key = blankToNull(request.idempotencyKey());
    if (key != null) {
      var existing = repository.findByIdempotencyKey(key);
      if (existing.isPresent()) {
        Payment prior = existing.get();
        if (!prior.getMemberId().equals(request.memberId())
            || !prior.getSubscriptionId().equals(request.subscriptionId())) {
          throw new ConflictException("Idempotency key already belongs to a different payment");
        }
        return toResponse(prior);
      }
    }

    SubscriptionView subscription = loadSubscription(request.subscriptionId());
    if (!subscription.memberId().equals(request.memberId())) {
      throw new IllegalArgumentException("Subscription does not belong to this member");
    }
    PlanView plan = loadPlan(subscription.planId());
    BigDecimal amount = plan.price();
    if (amount == null || amount.signum() < 0) {
      throw new IllegalArgumentException("Plan price is not payable");
    }

    PaymentStatus outcome = "SIMULATED_FAIL".equals(method) ? PaymentStatus.FAILED : PaymentStatus.SUCCESS;
    String transactionId = "sim-" + UUID.randomUUID();
    Payment payment =
        new Payment(
            request.memberId(),
            request.subscriptionId(),
            amount,
            method,
            transactionId,
            outcome,
            OffsetDateTime.now(),
            key,
            true,
            SIMULATED_NOTE);
    Payment saved = repository.save(payment);
    if (outcome == PaymentStatus.SUCCESS) {
      activateRequired(saved.getSubscriptionId());
    }
    notifyQuietly(saved, plan.planName());
    return toResponse(saved);
  }

  @Transactional
  public PaymentResponse retry(Long id) {
    Payment payment = require(id);
    if (!METHODS.contains(payment.getPaymentMethod()) || payment.getPaymentStatus() != PaymentStatus.FAILED) {
      throw new IllegalArgumentException("Only failed simulated payments can be retried");
    }
    PaymentStatus outcome =
        "SIMULATED_FAIL".equals(payment.getPaymentMethod()) ? PaymentStatus.FAILED : PaymentStatus.SUCCESS;
    payment.applyResult(outcome, "sim-" + UUID.randomUUID(), SIMULATED_NOTE);
    Payment saved = repository.save(payment);
    if (outcome == PaymentStatus.SUCCESS) {
      activateRequired(saved.getSubscriptionId());
    }
    notifyQuietly(saved, "membership");
    return toResponse(saved);
  }

  @Transactional
  public PaymentResponse refund(Long id) {
    Payment payment = require(id);
    if (payment.getPaymentStatus() == PaymentStatus.REFUNDED) {
      return toResponse(payment);
    }
    if (payment.getPaymentStatus() != PaymentStatus.SUCCESS) {
      throw new IllegalArgumentException("Only a successful simulated payment can be refunded");
    }
    payment.markRefunded(SIMULATED_NOTE + " Marked refunded in the ledger.");
    return toResponse(repository.save(payment));
  }

  public PaymentResponse get(Long id) {
    return toResponse(require(id));
  }

  public List<PaymentResponse> byMember(Long memberId) {
    return repository.findByMemberIdOrderByPaymentDateDesc(memberId).stream().map(this::toResponse).toList();
  }

  public List<PaymentResponse> bySubscription(Long subscriptionId) {
    return repository.findBySubscriptionIdOrderByPaymentDateDesc(subscriptionId).stream()
        .map(this::toResponse)
        .toList();
  }

  public List<PaymentResponse> all() {
    return repository.findAll().stream().map(this::toResponse).toList();
  }

  private SubscriptionView loadSubscription(Long id) {
    try {
      return subscriptions.get(id);
    } catch (FeignException.NotFound ex) {
      throw new ResourceNotFoundException("Subscription not found: " + id);
    } catch (FeignException ex) {
      throw new ServiceUnavailableException("Subscription service is unavailable");
    }
  }

  private PlanView loadPlan(Long id) {
    try {
      return plans.get(id);
    } catch (FeignException ex) {
      throw new ServiceUnavailableException("Subscription service is unavailable");
    }
  }

  private SubscriptionView prepare(Long memberId, Long planId) {
    try {
      return subscriptions.prepare(new CheckoutCommand(memberId, planId));
    } catch (FeignException.Conflict ex) {
      throw new ConflictException(messageOf(ex));
    } catch (FeignException.NotFound ex) {
      throw new ResourceNotFoundException("Plan or member was not found");
    } catch (FeignException ex) {
      throw new ServiceUnavailableException("Subscription service is unavailable");
    }
  }

  private void cancelQuietly(Long subscriptionId) {
    try {
      subscriptions.cancel(subscriptionId);
    } catch (FeignException ex) {
      log.warn("Payment updated but subscription {} was not cancelled: {}", subscriptionId, ex.status());
    }
  }

  private CheckoutResponse checkoutResponse(Payment payment, PlanView plan, SubscriptionView subscription) {
    return new CheckoutResponse(
        payment.getId(),
        subscription.id(),
        plan.id(),
        plan.planName(),
        plan.description(),
        plan.durationInDays(),
        plan.price(),
        "INR",
        paise(plan.price()),
        razorpay.keyId(),
        payment.getProviderOrderId(),
        payment.getPaymentStatus().name(),
        true,
        TEST_NOTE + " UPI is offered in Razorpay test checkout. Use success@razorpay or failure@razorpay.");
  }

  private int paise(BigDecimal amount) {
    if (amount == null || amount.signum() < 0) {
      throw new IllegalArgumentException("Plan price is not payable");
    }
    return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact();
  }

  private String messageOf(FeignException ex) {
    String body = ex.contentUTF8();
    int key = body == null ? -1 : body.indexOf("\"message\"");
    if (key < 0) return "You already have an active membership.";
    int start = body.indexOf('"', key + 10);
    int end = start < 0 ? -1 : body.indexOf('"', start + 1);
    if (start < 0 || end < 0) return "You already have an active membership.";
    return body.substring(start + 1, end);
  }

  private void activateRequired(Long subscriptionId) {
    try {
      subscriptions.activate(subscriptionId);
    } catch (FeignException ex) {
      throw new ServiceUnavailableException(
          "The payment could not activate the membership. Retry it; a second active membership will not be created.");
    }
  }

  private void notifyQuietly(Payment payment, String planName) {
    try {
      MemberView member = members.get(payment.getMemberId());
      if (member == null || member.email() == null || member.email().isBlank()) return;
      String type = payment.getPaymentStatus() == PaymentStatus.SUCCESS ? "PAYMENT_SUCCESS" : "PAYMENT_FAILED";
      String subject =
          payment.getPaymentStatus() == PaymentStatus.SUCCESS
              ? "PulseFit payment recorded"
              : "PulseFit payment failed";
      String message =
          "Simulated payment "
              + payment.getPaymentStatus()
              + " for "
              + planName
              + ". Amount "
              + payment.getAmount()
              + ". Reference "
              + payment.getTransactionId()
              + ". "
              + SIMULATED_NOTE;
      notifications.create(
          new NotificationRequest(payment.getMemberId(), type, member.email(), subject, message));
    } catch (Exception ex) {
      log.warn("Payment {} saved without a notification: {}", payment.getId(), ex.getMessage());
    }
  }

  private Payment require(Long id) {
    return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + id));
  }

  private PaymentResponse toResponse(Payment payment) {
    return new PaymentResponse(
        payment.getId(),
        payment.getMemberId(),
        payment.getSubscriptionId(),
        payment.getAmount(),
        payment.getPaymentMethod(),
        payment.getTransactionId(),
        payment.getPaymentStatus(),
        payment.getPaymentDate(),
        payment.getIdempotencyKey(),
        payment.isSimulated(),
        payment.getProviderMessage());
  }

  private String blankToNull(String value) {
    if (value == null || value.isBlank()) return null;
    return value.trim();
  }
}
