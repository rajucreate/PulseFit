package com.pulsefit.facility.model;

import jakarta.persistence.*;

@Entity
@Table(name = "facilities")
public class Facility {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String address;

  @Column(nullable = false)
  private String location;

  @Column(nullable = false)
  private String contact;

  @Column(nullable = false)
  private String operatingHours;

  @Column(nullable = false)
  private Integer capacity;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private FacilityStatus status;

  protected Facility() {}

  public Facility(
      String name,
      String address,
      String location,
      String contact,
      String operatingHours,
      Integer capacity,
      FacilityStatus status) {
    this.name = name;
    this.address = address;
    this.location = location;
    this.contact = contact;
    this.operatingHours = operatingHours;
    this.capacity = capacity;
    this.status = status;
  }

  public Long getId() { return id; }
  public String getName() { return name; }
  public String getAddress() { return address; }
  public String getLocation() { return location; }
  public String getContact() { return contact; }
  public String getOperatingHours() { return operatingHours; }
  public Integer getCapacity() { return capacity; }
  public FacilityStatus getStatus() { return status; }

  public void update(
      String name,
      String address,
      String location,
      String contact,
      String operatingHours,
      Integer capacity,
      FacilityStatus status) {
    this.name = name;
    this.address = address;
    this.location = location;
    this.contact = contact;
    this.operatingHours = operatingHours;
    this.capacity = capacity;
    this.status = status;
  }

  public void deactivate() {
    this.status = FacilityStatus.INACTIVE;
  }
}
