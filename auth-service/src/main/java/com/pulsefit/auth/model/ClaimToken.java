package com.pulsefit.auth.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "claim_tokens")
public class ClaimToken {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String email;

  @Column(name = "member_id", nullable = false)
  private Long memberId;

  @Column(name = "token_hash", nullable = false)
  private String tokenHash;

  @Column(name = "attempts_count", nullable = false)
  private int attemptsCount = 0;

  @Column(name = "max_attempts", nullable = false)
  private int maxAttempts = 3;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  @Column(name = "consumed_at")
  private LocalDateTime consumedAt;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  public ClaimToken() {}

  public ClaimToken(String email, Long memberId, String tokenHash, LocalDateTime expiresAt, int maxAttempts) {
    this.email = email;
    this.memberId = memberId;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
    this.maxAttempts = maxAttempts;
    this.attemptsCount = 0;
    this.createdAt = LocalDateTime.now();
  }

  @PrePersist
  public void prePersist() {
    if (createdAt == null) createdAt = LocalDateTime.now();
  }

  public boolean isExpired() {
    return LocalDateTime.now().isAfter(expiresAt);
  }

  public boolean isConsumed() {
    return consumedAt != null;
  }

  public boolean hasExceededAttempts() {
    return attemptsCount >= maxAttempts;
  }

  public void incrementAttempts() {
    this.attemptsCount++;
  }

  public void consume() {
    this.consumedAt = LocalDateTime.now();
  }

  public Long getId() { return id; }
  public String getEmail() { return email; }
  public Long getMemberId() { return memberId; }
  public String getTokenHash() { return tokenHash; }
  public int getAttemptsCount() { return attemptsCount; }
  public int getMaxAttempts() { return maxAttempts; }
  public LocalDateTime getExpiresAt() { return expiresAt; }
  public LocalDateTime getConsumedAt() { return consumedAt; }
  public LocalDateTime getCreatedAt() { return createdAt; }
}

