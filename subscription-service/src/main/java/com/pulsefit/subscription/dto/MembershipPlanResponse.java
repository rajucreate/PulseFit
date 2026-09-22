package com.pulsefit.subscription.dto;

import java.math.BigDecimal;

public record MembershipPlanResponse(
    Long id,
    String planName,
    Integer durationInDays,
    BigDecimal price,
    String description,
    boolean active) {}
