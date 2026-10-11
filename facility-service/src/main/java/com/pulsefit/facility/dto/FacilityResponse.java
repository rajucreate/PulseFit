package com.pulsefit.facility.dto;

import com.pulsefit.facility.model.FacilityStatus;

public record FacilityResponse(
    Long id,
    String name,
    String address,
    String location,
    String contact,
    String operatingHours,
    Integer capacity,
    FacilityStatus status) {}
