package com.pulsefit.auth.client;

import java.time.LocalDate;

public record CreateMemberRequest(
    String name,
    String email,
    String contact,
    LocalDate dateOfBirth,
    String status
) {}

