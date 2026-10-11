package com.pulsefit.subscription.service;

import com.pulsefit.subscription.model.Subscription;
import com.pulsefit.subscription.model.SubscriptionStatus;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Optional;

public final class MembershipRules {
  private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

  private MembershipRules() {}

  public static Optional<Subscription> unexpiredActive(Iterable<Subscription> rows, LocalDate today) {
    Subscription chosen = null;
    for (Subscription row : rows) {
      if (!blocksPurchase(row, today)) continue;
      if (chosen == null || row.getExpiryDate().isAfter(chosen.getExpiryDate())) {
        chosen = row;
      }
    }
    return Optional.ofNullable(chosen);
  }

  public static boolean blocksPurchase(Subscription row, LocalDate today) {
    return row.getStatus() == SubscriptionStatus.ACTIVE && !row.getExpiryDate().isBefore(today);
  }

  public static String blockMessage(LocalDate expiryDate) {
    return "You already have an active membership until "
        + DATE.format(expiryDate)
        + ". You can purchase another plan after it expires.";
  }

  public static Optional<Subscription> reusablePending(Iterable<Subscription> rows, Long planId) {
    Subscription chosen = null;
    for (Subscription row : rows) {
      if (row.getStatus() != SubscriptionStatus.PENDING || !row.getPlanId().equals(planId)) continue;
      if (chosen == null || later(row, chosen)) chosen = row;
    }
    return Optional.ofNullable(chosen);
  }

  private static boolean later(Subscription left, Subscription right) {
    return Comparator.nullsFirst(Long::compareTo).compare(left.getId(), right.getId()) > 0;
  }
}
