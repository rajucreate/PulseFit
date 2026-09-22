package com.pulsefit.auth.service;

import com.pulsefit.auth.dto.AuditLogResponse;
import com.pulsefit.auth.model.AuditLog;
import com.pulsefit.auth.repository.AuditLogRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {
  private final AuditLogRepository auditLogRepository;

  public AuditLogService(AuditLogRepository auditLogRepository) {
    this.auditLogRepository = auditLogRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void log(
      Long actorUserId,
      String actorEmail,
      String actorIpAddress,
      String action,
      Long targetUserId,
      String targetEmail,
      String details,
      String outcome,
      String failureReason) {
    try {
      AuditLog entry =
          new AuditLog(
              actorUserId,
              actorEmail,
              actorIpAddress,
              action,
              targetUserId,
              targetEmail,
              details,
              outcome,
              failureReason);
      auditLogRepository.save(entry);
    } catch (Exception e) {
      // Avoid failing application flow if audit storage fails unexpectedly
      System.err.println("CRITICAL: Failed to write audit log entry: " + e.getMessage());
    }
  }

  @Transactional(readOnly = true)
  public List<AuditLogResponse> getAllLogs() {
    return auditLogRepository.findAllByOrderByTimestampDesc().stream()
        .map(
            l ->
                new AuditLogResponse(
                    l.getId(),
                    l.getTimestamp(),
                    l.getActorUserId(),
                    l.getActorEmail(),
                    l.getActorIpAddress(),
                    l.getAction(),
                    l.getTargetUserId(),
                    l.getTargetEmail(),
                    l.getDetails(),
                    l.getOutcome(),
                    l.getFailureReason()))
        .toList();
  }
}

