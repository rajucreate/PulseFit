package com.pulsefit.member.dto;

import java.time.LocalDate;

public record SubscriptionView(
    Long id,
    Long memberId,
    Long planId,
    LocalDate startDate,
    LocalDate expiryDate,
    String status) {}
