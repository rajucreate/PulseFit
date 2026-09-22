package com.pulsefit.subscription.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "subscriptions")
public class Subscription {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long memberId;

  @Column(nullable = false)
  private Long planId;

  @Column(nullable = false)
  private LocalDate startDate;

  @Column(nullable = false)
  private LocalDate expiryDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SubscriptionStatus status;

  protected Subscription() {}

  public Subscription(
      Long memberId,
      Long planId,
      LocalDate startDate,
      LocalDate expiryDate,
      SubscriptionStatus status) {
    this.memberId = memberId;
    this.planId = planId;
    this.startDate = startDate;
    this.expiryDate = expiryDate;
    this.status = status;
  }

  public Long getId() {
    return id;
  }

  public Long getMemberId() {
    return memberId;
  }

  public Long getPlanId() {
    return planId;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public LocalDate getExpiryDate() {
    return expiryDate;
  }

  public SubscriptionStatus getStatus() {
    return status;
  }

  public void update(
      Long memberId, Long planId, LocalDate start, LocalDate expiry, SubscriptionStatus status) {
    this.memberId = memberId;
    this.planId = planId;
    this.startDate = start;
    this.expiryDate = expiry;
    this.status = status;
  }
}
