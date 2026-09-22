package com.pulsefit.member.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(
    name = "members",
    uniqueConstraints = @UniqueConstraint(name = "uk_member_email", columnNames = "email"))
public class Member {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(nullable = false)
  private String contact;

  private LocalDate dateOfBirth;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private MemberStatus status;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  protected Member() {}

  public Member(
      String name, String email, String contact, LocalDate dateOfBirth, MemberStatus status) {
    this.name = name;
    this.email = email;
    this.contact = contact;
    this.dateOfBirth = dateOfBirth;
    this.status = status;
  }

  @PrePersist
  void onCreate() {
    createdAt = OffsetDateTime.now();
    if (status == null) status = MemberStatus.ACTIVE;
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getEmail() {
    return email;
  }

  public String getContact() {
    return contact;
  }

  public LocalDate getDateOfBirth() {
    return dateOfBirth;
  }

  public MemberStatus getStatus() {
    return status;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void update(
      String name, String email, String contact, LocalDate dateOfBirth, MemberStatus status) {
    this.name = name;
    this.email = email;
    this.contact = contact;
    this.dateOfBirth = dateOfBirth;
    this.status = status;
  }
}
