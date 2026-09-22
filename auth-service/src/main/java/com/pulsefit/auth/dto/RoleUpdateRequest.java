package com.pulsefit.auth.dto;

import com.pulsefit.auth.model.Role;
import jakarta.validation.constraints.NotNull;

public record RoleUpdateRequest(
    @NotNull(message = "Role is required")
    Role role
) {}

