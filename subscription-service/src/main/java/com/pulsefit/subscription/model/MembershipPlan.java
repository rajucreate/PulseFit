package com.pulsefit.subscription.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "membership_plans")
public class MembershipPlan {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String planName;

  @Column(nullable = false)
  private Integer durationInDays;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal price;

  private String description;

  @Column(nullable = false)
  private boolean active;

  protected MembershipPlan() {}

  public MembershipPlan(
      String planName,
      Integer durationInDays,
      BigDecimal price,
      String description,
      boolean active) {
    this.planName = planName;
    this.durationInDays = durationInDays;
    this.price = price;
    this.description = description;
    this.active = active;
  }

  public Long getId() {
    return id;
  }

  public String getPlanName() {
    return planName;
  }

  public Integer getDurationInDays() {
    return durationInDays;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public String getDescription() {
    return description;
  }

  public boolean isActive() {
    return active;
  }

  public void update(
      String name, Integer duration, BigDecimal price, String description, boolean active) {
    this.planName = name;
    this.durationInDays = duration;
    this.price = price;
    this.description = description;
    this.active = active;
  }
}
