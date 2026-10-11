package com.pulsefit.payment.security;

public record AuthenticatedUser(
    Long userId,
    String email,
    String role,
    Long memberId
) {}

