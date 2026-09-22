package com.pulsefit.auth.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "auth_audit_logs")
public class AuditLog {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private LocalDateTime timestamp;

  @Column(name = "actor_user_id")
  private Long actorUserId;

  @Column(name = "actor_email")
  private String actorEmail;

  @Column(name = "actor_ip_address")
  private String actorIpAddress;

  @Column(nullable = false)
  private String action;

  @Column(name = "target_user_id")
  private Long targetUserId;

  @Column(name = "target_email")
  private String targetEmail;

  @Column(columnDefinition = "TEXT")
  private String details;

  @Column(nullable = false)
  private String outcome;

  @Column(name = "failure_reason", columnDefinition = "TEXT")
  private String failureReason;

  public AuditLog() {}

  public AuditLog(
      Long actorUserId,
      String actorEmail,
      String actorIpAddress,
      String action,
      Long targetUserId,
      String targetEmail,
      String details,
      String outcome,
      String failureReason) {
    this.timestamp = LocalDateTime.now();
    this.actorUserId = actorUserId;
    this.actorEmail = actorEmail;
    this.actorIpAddress = actorIpAddress;
    this.action = action;
    this.targetUserId = targetUserId;
    this.targetEmail = targetEmail;
    this.details = details;
    this.outcome = outcome;
    this.failureReason = failureReason;
  }

  @PrePersist
  public void prePersist() {
    if (timestamp == null) timestamp = LocalDateTime.now();
  }

  public Long getId() { return id; }
  public LocalDateTime getTimestamp() { return timestamp; }
  public Long getActorUserId() { return actorUserId; }
  public String getActorEmail() { return actorEmail; }
  public String getActorIpAddress() { return actorIpAddress; }
  public String getAction() { return action; }
  public Long getTargetUserId() { return targetUserId; }
  public String getTargetEmail() { return targetEmail; }
  public String getDetails() { return details; }
  public String getOutcome() { return outcome; }
  public String getFailureReason() { return failureReason; }
}

