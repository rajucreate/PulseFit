package com.pulsefit.subscription.security;

public record AuthenticatedUser(
    Long userId,
    String email,
    String role,
    Long memberId
) {}

