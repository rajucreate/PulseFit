package com.pulsefit.subscription;

import static org.junit.jupiter.api.Assertions.*;

import com.pulsefit.subscription.model.Subscription;
import com.pulsefit.subscription.model.SubscriptionStatus;
import com.pulsefit.subscription.service.MembershipRules;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MembershipRulesTest {
  private final LocalDate today = LocalDate.of(2026, 10, 11);

  @Test
  void unexpiredMembershipBlocksAnyOtherPlan() {
    Subscription current =
        new Subscription(4L, 1L, today.minusDays(2), today.plusDays(20), SubscriptionStatus.ACTIVE);
    assertTrue(MembershipRules.unexpiredActive(List.of(current), today).isPresent());
    assertTrue(MembershipRules.blockMessage(current.getExpiryDate()).contains("2026-10-31"));
  }

  @Test
  void expiredCancelledAndPendingDoNotBlock() {
    List<Subscription> rows =
        List.of(
            new Subscription(4L, 1L, today.minusDays(40), today.minusDays(1), SubscriptionStatus.ACTIVE),
            new Subscription(4L, 2L, today, today.plusDays(30), SubscriptionStatus.PENDING),
            new Subscription(4L, 3L, today, today.plusDays(30), SubscriptionStatus.CANCELLED));
    assertTrue(MembershipRules.unexpiredActive(rows, today).isEmpty());
  }
}
