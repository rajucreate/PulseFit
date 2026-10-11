package com.pulsefit.facility.dto;

import com.pulsefit.facility.model.FacilityStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record FacilityRequest(
    @NotBlank String name,
    @NotBlank String address,
    @NotBlank String location,
    @NotBlank String contact,
    @NotBlank String operatingHours,
    @NotNull @Positive Integer capacity,
    @NotNull FacilityStatus status) {}
