package com.pulsefit.attendance.client;

public record SubscriptionValidity(Long memberId, boolean valid, Long subscriptionId) {}
