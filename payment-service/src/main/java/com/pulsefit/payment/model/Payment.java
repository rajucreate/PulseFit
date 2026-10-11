package com.pulsefit.payment.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "payments")
public class Payment {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long memberId;

  @Column(nullable = false)
  private Long subscriptionId;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false)
  private String paymentMethod;

  @Column(nullable = false, unique = true)
  private String transactionId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PaymentStatus paymentStatus;

  @Column(nullable = false)
  private OffsetDateTime paymentDate;

  @Column(unique = true)
  private String idempotencyKey;

  @Column(nullable = false)
  private boolean simulated;

  @Column(length = 500)
  private String providerMessage;

  @Column(unique = true)
  private String providerOrderId;

  protected Payment() {}

  public Payment(
      Long memberId,
      Long subscriptionId,
      BigDecimal amount,
      String paymentMethod,
      String transactionId,
      PaymentStatus paymentStatus,
      OffsetDateTime paymentDate,
      String idempotencyKey,
      boolean simulated,
      String providerMessage) {
    this.memberId = memberId;
    this.subscriptionId = subscriptionId;
    this.amount = amount;
    this.paymentMethod = paymentMethod;
    this.transactionId = transactionId;
    this.paymentStatus = paymentStatus;
    this.paymentDate = paymentDate;
    this.idempotencyKey = idempotencyKey;
    this.simulated = simulated;
    this.providerMessage = providerMessage;
  }

  public Long getId() { return id; }
  public Long getMemberId() { return memberId; }
  public Long getSubscriptionId() { return subscriptionId; }
  public BigDecimal getAmount() { return amount; }
  public String getPaymentMethod() { return paymentMethod; }
  public String getTransactionId() { return transactionId; }
  public PaymentStatus getPaymentStatus() { return paymentStatus; }
  public OffsetDateTime getPaymentDate() { return paymentDate; }
  public String getIdempotencyKey() { return idempotencyKey; }
  public boolean isSimulated() { return simulated; }
  public String getProviderMessage() { return providerMessage; }

  public void attachOrder(String providerOrderId) {
    this.providerOrderId = providerOrderId;
  }

  public String getProviderOrderId() {
    return providerOrderId;
  }

  public void applyResult(PaymentStatus status, String transactionId, String providerMessage) {
    this.paymentStatus = status;
    this.transactionId = transactionId;
    this.providerMessage = providerMessage;
    this.paymentDate = OffsetDateTime.now();
  }

  public void markRefunded(String providerMessage) {
    this.paymentStatus = PaymentStatus.REFUNDED;
    this.providerMessage = providerMessage;
    this.paymentDate = OffsetDateTime.now();
  }
}
