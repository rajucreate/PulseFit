package com.pulsefit.auth.dto;

import java.time.LocalDateTime;

public record ClaimInitiateResponse(
    String message,
    LocalDateTime expiresAt,
    String devOtp
) {}

