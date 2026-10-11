package com.pulsefit.subscription.repository;

import com.pulsefit.subscription.model.*;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
  List<Subscription> findByMemberId(Long memberId);

  @Query(value = "SELECT CAST(pg_advisory_xact_lock(:memberKey) AS varchar)", nativeQuery = true)
  String lockMember(@Param("memberKey") long memberKey);
}
