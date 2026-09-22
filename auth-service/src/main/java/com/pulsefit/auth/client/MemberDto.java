package com.pulsefit.auth.client;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record MemberDto(
    Long id,
    String name,
    String email,
    String contact,
    LocalDate dateOfBirth,
    String status,
    OffsetDateTime createdAt
) {}

