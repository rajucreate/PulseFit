package com.pulsefit.member.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public record AssignSubscriptionRequest(
    @NotNull @Positive Long memberId,
    @NotNull @Positive Long planId,
    @NotNull LocalDate startDate,
    @NotNull LocalDate expiryDate,
    @NotNull String status) {}
