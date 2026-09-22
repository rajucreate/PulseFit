package com.pulsefit.subscription.dto;

import com.pulsefit.subscription.model.SubscriptionStatus;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record SubscriptionRequest(
    @NotNull @Positive Long memberId,
    @NotNull @Positive Long planId,
    @NotNull LocalDate startDate,
    @NotNull LocalDate expiryDate,
    @NotNull SubscriptionStatus status) {}
