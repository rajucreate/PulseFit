package com.pulsefit.subscription.client;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record MemberClientResponse(
    Long id,
    String name,
    String email,
    String contact,
    LocalDate dateOfBirth,
    String status,
    OffsetDateTime createdAt) {}

