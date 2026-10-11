package com.pulsefit.payment.controller;

import com.pulsefit.payment.dto.CheckoutDtos.CancelRequest;
import com.pulsefit.payment.dto.CheckoutDtos.CheckoutRequest;
import com.pulsefit.payment.dto.CheckoutDtos.CheckoutResponse;
import com.pulsefit.payment.dto.CheckoutDtos.VerifyRequest;
import com.pulsefit.payment.dto.PaymentRequest;
import com.pulsefit.payment.dto.PaymentResponse;
import com.pulsefit.payment.security.AuthenticatedUser;
import com.pulsefit.payment.service.PaymentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
  private final PaymentService service;

  public PaymentController(PaymentService service) {
    this.service = service;
  }

  @PostMapping("/checkout")
  public CheckoutResponse checkout(@Valid @RequestBody CheckoutRequest request, Authentication authentication) {
    return service.checkout(requireSelf(authentication), request.planId());
  }

  @PostMapping("/verify")
  public PaymentResponse verify(@Valid @RequestBody VerifyRequest request, Authentication authentication) {
    return service.verify(requireSelf(authentication), request);
  }

  @PostMapping("/checkout/cancel")
  public PaymentResponse cancel(@Valid @RequestBody CancelRequest request, Authentication authentication) {
    return service.cancelCheckout(requireSelf(authentication), request);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  public PaymentResponse create(@Valid @RequestBody PaymentRequest request, Authentication authentication) {
    enforce(request.memberId(), authentication, "pay for");
    return service.initiate(request);
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  public List<PaymentResponse> all() {
    return service.all();
  }

  @GetMapping("/{id}")
  public PaymentResponse get(@PathVariable Long id, Authentication authentication) {
    PaymentResponse response = service.get(id);
    enforce(response.memberId(), authentication, "view payment of");
    return response;
  }

  @GetMapping("/member/{memberId}")
  public List<PaymentResponse> byMember(@PathVariable Long memberId, Authentication authentication) {
    enforce(memberId, authentication, "view payments for");
    return service.byMember(memberId);
  }

  @GetMapping("/subscription/{subscriptionId}")
  public List<PaymentResponse> bySubscription(
      @PathVariable Long subscriptionId, Authentication authentication) {
    List<PaymentResponse> rows = service.bySubscription(subscriptionId);
    if (authentication != null
        && authentication.getPrincipal() instanceof AuthenticatedUser user
        && "MEMBER".equalsIgnoreCase(user.role())) {
      rows.forEach(row -> enforce(row.memberId(), authentication, "view payments for"));
    }
    return rows;
  }

  @PostMapping("/{id}/retry")
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  public PaymentResponse retry(@PathVariable Long id, Authentication authentication) {
    enforce(service.get(id).memberId(), authentication, "retry payment of");
    return service.retry(id);
  }

  @PostMapping("/{id}/refund")
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  public PaymentResponse refund(@PathVariable Long id) {
    return service.refund(id);
  }

  private Long requireSelf(Authentication authentication) {
    if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
      if (user.memberId() == null) {
        throw new AccessDeniedException("This account is not linked to a member profile.");
      }
      if ("MEMBER".equalsIgnoreCase(user.role())
          || "STAFF".equalsIgnoreCase(user.role())
          || "ADMIN".equalsIgnoreCase(user.role())) {
        return user.memberId();
      }
    }
    throw new AccessDeniedException("Sign in as a member to check out.");
  }

  private void enforce(Long memberId, Authentication authentication, String action) {
    if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
      if ("MEMBER".equalsIgnoreCase(user.role())
          && (user.memberId() == null || !user.memberId().equals(memberId))) {
        throw new AccessDeniedException(
            "Access denied: You can only " + action + " your own member ID (" + user.memberId() + ").");
      }
    }
  }
}
