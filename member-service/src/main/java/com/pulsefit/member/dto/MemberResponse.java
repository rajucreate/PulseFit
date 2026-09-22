package com.pulsefit.member.dto;

import com.pulsefit.member.model.MemberStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record MemberResponse(
    Long id,
    String name,
    String email,
    String contact,
    LocalDate dateOfBirth,
    MemberStatus status,
    OffsetDateTime createdAt) {}
