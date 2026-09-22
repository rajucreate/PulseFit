package com.pulsefit.subscription.dto;

import com.pulsefit.subscription.model.SubscriptionStatus;
import java.time.LocalDate;

public record SubscriptionResponse(
    Long id,
    Long memberId,
    Long planId,
    LocalDate startDate,
    LocalDate expiryDate,
    SubscriptionStatus status) {}
