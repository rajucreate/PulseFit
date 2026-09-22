package com.pulsefit.auth.dto;

import com.pulsefit.auth.model.Role;

public record UserSummary(
    Long userId,
    String email,
    String firstName,
    String lastName,
    Role role,
    Long memberId,
    boolean enabled,
    Long tokenVersion
) {}

