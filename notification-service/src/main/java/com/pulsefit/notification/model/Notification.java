package com.pulsefit.notification.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "notifications")
public class Notification {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long memberId;

  @Column(nullable = false)
  private String type;

  @Column(nullable = false)
  private String recipient;

  @Column(nullable = false)
  private String subject;

  @Column(nullable = false, length = 2000)
  private String message;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private NotificationStatus status;

  @Column(nullable = false)
  private OffsetDateTime createdAt;

  @Column(length = 500)
  private String deliveryNote;

  private int attempts;

  protected Notification() {}

  public Notification(
      Long memberId, String type, String recipient, String subject, String message, OffsetDateTime createdAt) {
    this.memberId = memberId;
    this.type = type;
    this.recipient = recipient;
    this.subject = subject;
    this.message = message;
    this.createdAt = createdAt;
    this.status = NotificationStatus.PENDING;
    this.attempts = 0;
  }

  public Long getId() { return id; }
  public Long getMemberId() { return memberId; }
  public String getType() { return type; }
  public String getRecipient() { return recipient; }
  public String getSubject() { return subject; }
  public String getMessage() { return message; }
  public NotificationStatus getStatus() { return status; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public String getDeliveryNote() { return deliveryNote; }
  public int getAttempts() { return attempts; }

  public void recordAttempt(NotificationStatus status, String deliveryNote) {
    this.attempts += 1;
    this.status = status;
    this.deliveryNote = deliveryNote;
  }
}
