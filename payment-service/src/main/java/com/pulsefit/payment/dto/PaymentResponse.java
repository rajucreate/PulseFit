package com.pulsefit.payment.dto;

import com.pulsefit.payment.model.PaymentStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PaymentResponse(
    Long id,
    Long memberId,
    Long subscriptionId,
    BigDecimal amount,
    String paymentMethod,
    String transactionId,
    PaymentStatus paymentStatus,
    OffsetDateTime paymentDate,
    String idempotencyKey,
    boolean simulated,
    String providerMessage) {}
