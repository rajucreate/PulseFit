package com.pulsefit.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class CheckoutDtos {
  public record CheckoutRequest(@NotNull Long planId) {}

  public record VerifyRequest(
      @NotBlank String razorpayOrderId,
      @NotBlank String razorpayPaymentId,
      @NotBlank String razorpaySignature) {}

  public record CancelRequest(@NotBlank String orderId) {}

  public record CheckoutResponse(
      Long paymentId,
      Long subscriptionId,
      Long planId,
      String planName,
      String description,
      Integer durationInDays,
      BigDecimal amount,
      String currency,
      Integer amountPaise,
      String keyId,
      String orderId,
      String paymentStatus,
      boolean testMode,
      String message) {}
}
