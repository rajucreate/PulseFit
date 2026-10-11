package com.pulsefit.payment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.pulsefit.payment.client.Clients.NotificationClient;
import com.pulsefit.payment.client.Clients.PlanClient;
import com.pulsefit.payment.client.Clients.PlanView;
import com.pulsefit.payment.client.Clients.SubscriptionClient;
import com.pulsefit.payment.client.Clients.SubscriptionView;
import com.pulsefit.payment.client.Clients.CheckoutCommand;
import com.pulsefit.payment.dto.CheckoutDtos.VerifyRequest;
import com.pulsefit.payment.dto.PaymentRequest;
import com.pulsefit.payment.dto.PaymentResponse;
import com.pulsefit.payment.razorpay.RazorpayGateway;
import com.pulsefit.payment.exception.ConflictException;
import com.pulsefit.payment.model.Payment;
import com.pulsefit.payment.model.PaymentStatus;
import com.pulsefit.payment.repository.PaymentRepository;
import com.pulsefit.payment.service.PaymentService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
  @Mock private PaymentRepository repository;
  @Mock private SubscriptionClient subscriptions;
  @Mock private PlanClient plans;
  @Mock private com.pulsefit.payment.client.Clients.MemberClient members;
  @Mock private NotificationClient notifications;
  @Mock private RazorpayGateway razorpay;
  private PaymentService service;

  @BeforeEach
  void setUp() {
    service = new PaymentService(repository, subscriptions, plans, members, notifications, razorpay);
  }

  @Test
  void amountComesFromThePlanAndSuccessIsSimulated() {
    when(subscriptions.get(9L))
        .thenReturn(new SubscriptionView(9L, 3L, 2L, LocalDate.now(), LocalDate.now().plusDays(30), "ACTIVE"));
    when(plans.get(2L))
        .thenReturn(new PlanView(2L, "Monthly", 30, new BigDecimal("29.99"), "Standard", true));
    when(repository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

    PaymentResponse response =
        service.initiate(new PaymentRequest(3L, 9L, "SIMULATED_CARD", null));

    assertEquals(new BigDecimal("29.99"), response.amount());
    assertEquals(PaymentStatus.SUCCESS, response.paymentStatus());
    assertTrue(response.simulated());
    assertTrue(response.providerMessage().contains("Simulated"));
    verify(subscriptions).activate(9L);
  }

  @Test
  void simulatedFailureDoesNotActivateTheSubscription() {
    when(subscriptions.get(9L))
        .thenReturn(new SubscriptionView(9L, 3L, 2L, LocalDate.now(), LocalDate.now().plusDays(30), "ACTIVE"));
    when(plans.get(2L)).thenReturn(new PlanView(2L, "Monthly", 30, new BigDecimal("10.00"), "", true));
    when(repository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

    PaymentResponse response = service.initiate(new PaymentRequest(3L, 9L, "simulated_fail", "key-1"));

    assertEquals(PaymentStatus.FAILED, response.paymentStatus());
    verify(subscriptions, never()).activate(any());
  }

  @Test
  void sameIdempotencyKeyReturnsTheOriginalPayment() {
    Payment prior =
        new Payment(
            3L,
            9L,
            new BigDecimal("10.00"),
            "SIMULATED_CARD",
            "sim-existing",
            PaymentStatus.SUCCESS,
            java.time.OffsetDateTime.now(),
            "key-1",
            true,
            "Simulated");
    when(repository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(prior));

    PaymentResponse response = service.initiate(new PaymentRequest(3L, 9L, "SIMULATED_CARD", "key-1"));

    assertEquals("sim-existing", response.transactionId());
    verify(repository, never()).save(any());
  }

  @Test
  void idempotencyKeyCannotBeReusedForAnotherSubscription() {
    Payment prior =
        new Payment(
            3L, 9L, new BigDecimal("10.00"), "SIMULATED_CARD", "sim-existing",
            PaymentStatus.SUCCESS, java.time.OffsetDateTime.now(), "key-1", true, "Simulated");
    when(repository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(prior));
    assertThrows(
        ConflictException.class,
        () -> service.initiate(new PaymentRequest(3L, 10L, "SIMULATED_CARD", "key-1")));
  }

  @Test
  void refundMovesSuccessToRefundedOnce() {
    Payment payment =
        new Payment(
            3L, 9L, new BigDecimal("10.00"), "SIMULATED_CARD", "sim-1",
            PaymentStatus.SUCCESS, java.time.OffsetDateTime.now(), null, true, "Simulated");
    setId(payment, 5L);
    when(repository.findById(5L)).thenReturn(Optional.of(payment));
    when(repository.save(payment)).thenReturn(payment);

    PaymentResponse response = service.refund(5L);
    assertEquals(PaymentStatus.REFUNDED, response.paymentStatus());
    assertEquals(PaymentStatus.REFUNDED, service.refund(5L).paymentStatus());
  }

  @Test
  void invalidSignatureDoesNotActivate() {
    Payment payment = pendingOrder();
    when(repository.findByProviderOrderId("order_1")).thenReturn(Optional.of(payment));
    when(razorpay.signatureValid("order_1", "pay_1", "bad")).thenReturn(false);

    assertThrows(
        IllegalArgumentException.class,
        () -> service.verify(3L, new VerifyRequest("order_1", "pay_1", "bad")));
    verify(subscriptions, never()).activate(any());
  }

  @Test
  void amountMismatchDoesNotActivate() {
    Payment payment = pendingOrder();
    when(repository.findByProviderOrderId("order_1")).thenReturn(Optional.of(payment));
    when(razorpay.signatureValid(any(), any(), any())).thenReturn(true);
    when(razorpay.fetch("pay_1"))
        .thenReturn(new RazorpayGateway.RazorpayPayment("pay_1", "order_1", 1, "captured"));

    assertThrows(
        IllegalArgumentException.class,
        () -> service.verify(3L, new VerifyRequest("order_1", "pay_1", "sig")));
    verify(subscriptions, never()).activate(any());
  }

  @Test
  void verifiedCaptureActivatesOnce() {
    Payment payment = pendingOrder();
    when(repository.findByProviderOrderId("order_1")).thenReturn(Optional.of(payment));
    when(razorpay.signatureValid("order_1", "pay_1", "sig")).thenReturn(true);
    when(razorpay.fetch("pay_1"))
        .thenReturn(new RazorpayGateway.RazorpayPayment("pay_1", "order_1", 2999, "captured"));
    when(repository.save(payment)).thenReturn(payment);

    PaymentResponse first = service.verify(3L, new VerifyRequest("order_1", "pay_1", "sig"));
    PaymentResponse second = service.verify(3L, new VerifyRequest("order_1", "pay_1", "sig"));

    assertEquals(PaymentStatus.SUCCESS, first.paymentStatus());
    assertEquals(PaymentStatus.SUCCESS, second.paymentStatus());
    verify(subscriptions, times(1)).activate(9L);
  }

  @Test
  void checkoutUsesPlanPriceAndDoesNotActivate() {
    when(razorpay.configured()).thenReturn(true);
    when(razorpay.keyId()).thenReturn("rzp_test_demo");
    when(subscriptions.prepare(new CheckoutCommand(3L, 2L)))
        .thenReturn(new SubscriptionView(9L, 3L, 2L, LocalDate.now(), LocalDate.now().plusDays(30), "PENDING"));
    when(plans.get(2L))
        .thenReturn(new PlanView(2L, "Monthly", 30, new BigDecimal("29.99"), "Standard", true));
    when(repository.findFirstBySubscriptionIdAndPaymentStatus(9L, PaymentStatus.PENDING))
        .thenReturn(Optional.empty());
    when(razorpay.createOrder(2999, "sub_9"))
        .thenReturn(new RazorpayGateway.RazorpayOrder("order_1", 2999, "created"));
    when(repository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

    var response = service.checkout(3L, 2L);

    assertEquals(2999, response.amountPaise());
    assertEquals("order_1", response.orderId());
    assertEquals("rzp_test_demo", response.keyId());
    assertTrue(response.testMode());
    verify(subscriptions, never()).activate(any());
  }

  private Payment pendingOrder() {
    Payment payment =
        new Payment(
            3L,
            9L,
            new BigDecimal("29.99"),
            "RAZORPAY_UPI",
            "order_1",
            PaymentStatus.PENDING,
            java.time.OffsetDateTime.now(),
            "rzp-order_1",
            false,
            "test");
    payment.attachOrder("order_1");
    return payment;
  }

  private void setId(Payment payment, Long id) {
    try {
      var field = Payment.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(payment, id);
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException(ex);
    }
  }
}
