package com.pulsefit.auth.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "users",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_user_email", columnNames = {"email"})
    })
public class User {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(nullable = false)
  private String passwordHash;

  @Column(nullable = false)
  private String firstName;

  @Column(nullable = false)
  private String lastName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Role role;

  @Column(name = "member_id")
  private Long memberId;

  @Column(nullable = false)
  private boolean enabled = true;

  @Column(name = "token_version", nullable = false)
  private Long tokenVersion = 1L;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @Column(nullable = false)
  private LocalDateTime updatedAt;

  public User() {}

  public User(
      String email,
      String passwordHash,
      String firstName,
      String lastName,
      Role role,
      Long memberId) {
    this.email = email;
    this.passwordHash = passwordHash;
    this.firstName = firstName;
    this.lastName = lastName;
    this.role = role;
    this.memberId = memberId;
    this.enabled = true;
    this.tokenVersion = 1L;
    this.createdAt = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
  }

  @PrePersist
  public void prePersist() {
    if (createdAt == null) createdAt = LocalDateTime.now();
    if (updatedAt == null) updatedAt = LocalDateTime.now();
    if (tokenVersion == null) tokenVersion = 1L;
  }

  @PreUpdate
  public void preUpdate() {
    this.updatedAt = LocalDateTime.now();
  }

  public void incrementTokenVersion() {
    this.tokenVersion = (this.tokenVersion == null ? 1L : this.tokenVersion) + 1L;
  }

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }

  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }

  public String getPasswordHash() { return passwordHash; }
  public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

  public String getFirstName() { return firstName; }
  public void setFirstName(String firstName) { this.firstName = firstName; }

  public String getLastName() { return lastName; }
  public void setLastName(String lastName) { this.lastName = lastName; }

  public Role getRole() { return role; }
  public void setRole(Role role) { this.role = role; }

  public Long getMemberId() { return memberId; }
  public void setMemberId(Long memberId) { this.memberId = memberId; }

  public boolean isEnabled() { return enabled; }
  public void setEnabled(boolean enabled) { this.enabled = enabled; }

  public Long getTokenVersion() { return tokenVersion; }
  public void setTokenVersion(Long tokenVersion) { this.tokenVersion = tokenVersion; }

  public LocalDateTime getCreatedAt() { return createdAt; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
}

