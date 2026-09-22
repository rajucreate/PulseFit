package com.pulsefit.subscription.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record MembershipPlanRequest(
    @NotBlank String planName,
    @NotNull @Positive Integer durationInDays,
    @NotNull @PositiveOrZero BigDecimal price,
    String description,
    boolean active) {}
