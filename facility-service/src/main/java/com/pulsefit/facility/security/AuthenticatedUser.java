package com.pulsefit.facility.security;

public record AuthenticatedUser(
    Long userId,
    String email,
    String role,
    Long memberId
) {}

