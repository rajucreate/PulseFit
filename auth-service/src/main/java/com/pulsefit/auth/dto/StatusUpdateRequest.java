package com.pulsefit.auth.dto;

import jakarta.validation.constraints.NotNull;

public record StatusUpdateRequest(
    @NotNull(message = "Enabled flag is required")
    Boolean enabled
) {}

