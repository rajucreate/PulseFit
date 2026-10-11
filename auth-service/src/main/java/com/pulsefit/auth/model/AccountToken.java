package com.pulsefit.auth.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "account_tokens")
public class AccountToken {
  public enum Purpose {
    INVITE,
    RESET
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String email;

  @Column(nullable = false)
  private String fullName;

  @Column(nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Purpose purpose;

  @Column(nullable = false)
  private LocalDateTime expiresAt;

  private LocalDateTime consumedAt;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  protected AccountToken() {}

  public AccountToken(
      String email, String fullName, String tokenHash, Purpose purpose, LocalDateTime expiresAt) {
    this.email = email;
    this.fullName = fullName;
    this.tokenHash = tokenHash;
    this.purpose = purpose;
    this.expiresAt = expiresAt;
    this.createdAt = LocalDateTime.now();
  }

  public void rotate(String tokenHash, LocalDateTime expiresAt) {
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
    this.consumedAt = null;
    this.createdAt = LocalDateTime.now();
  }

  public Long getId() { return id; }
  public String getEmail() { return email; }
  public String getFullName() { return fullName; }
  public String getTokenHash() { return tokenHash; }
  public Purpose getPurpose() { return purpose; }
  public LocalDateTime getExpiresAt() { return expiresAt; }
  public LocalDateTime getConsumedAt() { return consumedAt; }
  public LocalDateTime getCreatedAt() { return createdAt; }
}
