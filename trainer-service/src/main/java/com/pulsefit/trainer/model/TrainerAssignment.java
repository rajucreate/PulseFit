package com.pulsefit.trainer.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(
    name = "trainer_assignments",
    uniqueConstraints = @UniqueConstraint(name = "uk_trainer_member", columnNames = {"trainerId", "memberId"}))
public class TrainerAssignment {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long trainerId;

  @Column(nullable = false)
  private Long memberId;

  @Column(nullable = false)
  private OffsetDateTime assignedAt;

  protected TrainerAssignment() {}

  public TrainerAssignment(Long trainerId, Long memberId, OffsetDateTime assignedAt) {
    this.trainerId = trainerId;
    this.memberId = memberId;
    this.assignedAt = assignedAt;
  }

  public Long getId() { return id; }
  public Long getTrainerId() { return trainerId; }
  public Long getMemberId() { return memberId; }
  public OffsetDateTime getAssignedAt() { return assignedAt; }
}
