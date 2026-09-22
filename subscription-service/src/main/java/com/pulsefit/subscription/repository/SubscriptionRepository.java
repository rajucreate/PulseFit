package com.pulsefit.subscription.repository;

import com.pulsefit.subscription.model.*;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
  List<Subscription> findByMemberId(Long memberId);
}
