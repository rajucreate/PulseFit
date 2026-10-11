package com.pulsefit.payment.repository;

import com.pulsefit.payment.model.Payment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
  Optional<Payment> findByIdempotencyKey(String idempotencyKey);

  Optional<Payment> findByProviderOrderId(String providerOrderId);

  Optional<Payment> findFirstBySubscriptionIdAndPaymentStatus(Long subscriptionId, com.pulsefit.payment.model.PaymentStatus paymentStatus);

  List<Payment> findByMemberIdOrderByPaymentDateDesc(Long memberId);

  List<Payment> findBySubscriptionIdOrderByPaymentDateDesc(Long subscriptionId);
}
