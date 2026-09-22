package com.pulsefit.attendance.dto;

import com.pulsefit.attendance.model.AccessStatus;
import java.time.OffsetDateTime;

public record AttendanceResponse(
    Long id,
    Long memberId,
    Long subscriptionId,
    Long facilityId,
    OffsetDateTime checkInTime,
    AccessStatus accessStatus,
    String denialReason) {}
