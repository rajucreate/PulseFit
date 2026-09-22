package com.pulsefit.subscription.dto;

public record ValidityResponse(Long memberId, boolean valid, Long subscriptionId) {}
