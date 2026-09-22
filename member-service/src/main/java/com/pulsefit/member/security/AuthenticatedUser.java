package com.pulsefit.member.security;

public record AuthenticatedUser(
    Long userId,
    String email,
    String role,
    Long memberId
) {}

