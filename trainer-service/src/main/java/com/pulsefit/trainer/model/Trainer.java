package com.pulsefit.trainer.model;

import jakarta.persistence.*;

@Entity
@Table(name = "trainers")
public class Trainer {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String specialization;

  @Column(nullable = false)
  private Integer experienceYears;

  @Column(nullable = false)
  private Long facilityId;

  @Column(nullable = false)
  private String availability;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TrainerStatus status;

  protected Trainer() {}

  public Trainer(
      String name,
      String specialization,
      Integer experienceYears,
      Long facilityId,
      String availability,
      TrainerStatus status) {
    this.name = name;
    this.specialization = specialization;
    this.experienceYears = experienceYears;
    this.facilityId = facilityId;
    this.availability = availability;
    this.status = status;
  }

  public Long getId() { return id; }
  public String getName() { return name; }
  public String getSpecialization() { return specialization; }
  public Integer getExperienceYears() { return experienceYears; }
  public Long getFacilityId() { return facilityId; }
  public String getAvailability() { return availability; }
  public TrainerStatus getStatus() { return status; }

  public void update(
      String name,
      String specialization,
      Integer experienceYears,
      Long facilityId,
      String availability,
      TrainerStatus status) {
    this.name = name;
    this.specialization = specialization;
    this.experienceYears = experienceYears;
    this.facilityId = facilityId;
    this.availability = availability;
    this.status = status;
  }

  public void deactivate() { this.status = TrainerStatus.INACTIVE; }
}
