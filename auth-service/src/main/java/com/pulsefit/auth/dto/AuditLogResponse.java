package com.pulsefit.auth.dto;

import java.time.LocalDateTime;

public record AuditLogResponse(
    Long id,
    LocalDateTime timestamp,
    Long actorUserId,
    String actorEmail,
    String actorIpAddress,
    String action,
    Long targetUserId,
    String targetEmail,
    String details,
    String outcome,
    String failureReason
) {}

