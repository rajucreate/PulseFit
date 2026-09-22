package com.pulsefit.attendance.security;

public record AuthenticatedUser(
    Long userId,
    String email,
    String role,
    Long memberId
) {}

