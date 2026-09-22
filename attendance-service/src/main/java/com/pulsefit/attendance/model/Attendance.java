package com.pulsefit.attendance.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "attendance")
public class Attendance {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long memberId;

  private Long subscriptionId;

  @Column(nullable = false)
  private Long facilityId;

  @Column(nullable = false)
  private OffsetDateTime checkInTime;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AccessStatus accessStatus;

  private String denialReason;

  protected Attendance() {}

  public Attendance(
      Long memberId,
      Long subscriptionId,
      Long facilityId,
      OffsetDateTime checkInTime,
      AccessStatus accessStatus,
      String denialReason) {
    this.memberId = memberId;
    this.subscriptionId = subscriptionId;
    this.facilityId = facilityId;
    this.checkInTime = checkInTime;
    this.accessStatus = accessStatus;
    this.denialReason = denialReason;
  }

  public Long getId() {
    return id;
  }

  public Long getMemberId() {
    return memberId;
  }

  public Long getSubscriptionId() {
    return subscriptionId;
  }

  public Long getFacilityId() {
    return facilityId;
  }

  public OffsetDateTime getCheckInTime() {
    return checkInTime;
  }

  public AccessStatus getAccessStatus() {
    return accessStatus;
  }

  public String getDenialReason() {
    return denialReason;
  }
}
