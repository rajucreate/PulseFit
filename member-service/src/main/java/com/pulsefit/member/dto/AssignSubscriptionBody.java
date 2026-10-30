package com.pulsefit.member.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public record AssignSubscriptionBody(
    @NotNull @Positive Long planId,
    LocalDate startDate,
    LocalDate expiryDate,
    String status) {}
