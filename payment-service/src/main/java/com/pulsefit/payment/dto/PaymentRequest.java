package com.pulsefit.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PaymentRequest(
    @NotNull @Positive Long memberId,
    @NotNull @Positive Long subscriptionId,
    @NotBlank String paymentMethod,
    String idempotencyKey) {}
