package com.pulsefit.attendance.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CheckInRequest(
    @NotNull @Positive Long memberId, @NotNull @Positive Long facilityId) {}
