package com.pulsefit.reporting.service;

import com.pulsefit.reporting.client.ReportClients.AttendanceView;
import com.pulsefit.reporting.client.ReportClients.FacilityView;
import com.pulsefit.reporting.client.ReportClients.MemberView;
import com.pulsefit.reporting.client.ReportClients.PaymentView;
import com.pulsefit.reporting.client.ReportClients.SubscriptionView;
import com.pulsefit.reporting.client.ReportClients.TrainerView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ReportMath {
  private ReportMath() {}

  public static Map<String, Object> members(List<MemberView> rows) {
    long active = rows.stream().filter(row -> "ACTIVE".equalsIgnoreCase(row.status())).count();
    long inactive = rows.size() - active;
    return Map.of("total", rows.size(), "active", active, "inactive", inactive);
  }

  public static Map<String, Object> subscriptions(List<SubscriptionView> rows) {
    long active = countStatus(rows, "ACTIVE");
    long expired = countStatus(rows, "EXPIRED");
    long cancelled = countStatus(rows, "CANCELLED");
    return Map.of("total", rows.size(), "active", active, "expired", expired, "cancelled", cancelled);
  }

  public static Map<String, Object> attendance(List<AttendanceView> rows, LocalDate from, LocalDate to, Long facilityId) {
    List<AttendanceView> filtered =
        rows.stream()
            .filter(row -> facilityId == null || facilityId.equals(row.facilityId()))
            .filter(row -> inRange(row.checkInTime() == null ? null : row.checkInTime().toLocalDate(), from, to))
            .toList();
    long granted = filtered.stream().filter(row -> "GRANTED".equalsIgnoreCase(row.accessStatus())).count();
    long denied = filtered.stream().filter(row -> "DENIED".equalsIgnoreCase(row.accessStatus())).count();
    LocalDate today = LocalDate.now();
    long todayCount =
        filtered.stream()
            .filter(row -> row.checkInTime() != null && today.equals(row.checkInTime().toLocalDate()))
            .count();
    Map<Long, long[]> byFacility = new LinkedHashMap<>();
    for (AttendanceView row : filtered) {
      long[] bucket = byFacility.computeIfAbsent(row.facilityId(), key -> new long[2]);
      if ("GRANTED".equalsIgnoreCase(row.accessStatus())) bucket[0] += 1;
      else bucket[1] += 1;
    }
    List<Map<String, Object>> facilities = new ArrayList<>();
    byFacility.forEach(
        (id, counts) ->
            facilities.add(Map.of("facilityId", id, "granted", counts[0], "denied", counts[1])));
    return Map.of(
        "total", filtered.size(),
        "today", todayCount,
        "granted", granted,
        "denied", denied,
        "byFacility", facilities);
  }

  public static Map<String, Object> revenue(List<PaymentView> rows, LocalDate from, LocalDate to) {
    Map<String, PaymentView> unique = new LinkedHashMap<>();
    for (PaymentView row : rows) {
      if (row.transactionId() != null) unique.putIfAbsent(row.transactionId(), row);
    }
    BigDecimal total = BigDecimal.ZERO;
    BigDecimal today = BigDecimal.ZERO;
    BigDecimal month = BigDecimal.ZERO;
    long success = 0;
    long failed = 0;
    long refunded = 0;
    LocalDate now = LocalDate.now();
    YearMonth thisMonth = YearMonth.from(now);
    List<Map<String, Object>> daily = new ArrayList<>();
    Map<LocalDate, BigDecimal> byDay = new LinkedHashMap<>();
    for (PaymentView row : unique.values()) {
      String status = row.paymentStatus() == null ? "" : row.paymentStatus();
      LocalDate day = row.paymentDate() == null ? null : row.paymentDate().toLocalDate();
      if ("FAILED".equalsIgnoreCase(status)) {
        failed += 1;
        continue;
      }
      if ("REFUNDED".equalsIgnoreCase(status)) {
        refunded += 1;
        continue;
      }
      if (!"SUCCESS".equalsIgnoreCase(status) || row.amount() == null) continue;
      if (!inRange(day, from, to)) continue;
      success += 1;
      total = total.add(row.amount());
      if (day != null && day.equals(now)) today = today.add(row.amount());
      if (day != null && YearMonth.from(day).equals(thisMonth)) month = month.add(row.amount());
      if (day != null) byDay.merge(day, row.amount(), BigDecimal::add);
    }
    byDay.forEach((day, amount) -> daily.add(Map.of("date", day.toString(), "amount", amount)));
    return Map.of(
        "total", total,
        "today", today,
        "month", month,
        "successfulPayments", success,
        "failedPayments", failed,
        "refundedPayments", refunded,
        "daily", daily);
  }

  public static Map<String, Object> facilities(List<FacilityView> rows) {
    long active = rows.stream().filter(row -> "ACTIVE".equalsIgnoreCase(row.status())).count();
    return Map.of("total", rows.size(), "active", active, "inactive", rows.size() - active);
  }

  public static Map<String, Object> trainers(List<TrainerView> rows) {
    long active = rows.stream().filter(row -> "ACTIVE".equalsIgnoreCase(row.status())).count();
    return Map.of("total", rows.size(), "active", active, "inactive", rows.size() - active);
  }

  private static long countStatus(List<SubscriptionView> rows, String status) {
    return rows.stream().filter(row -> status.equalsIgnoreCase(row.status())).count();
  }

  private static boolean inRange(LocalDate day, LocalDate from, LocalDate to) {
    if (day == null) return from == null && to == null;
    if (from != null && day.isBefore(from)) return false;
    if (to != null && day.isAfter(to)) return false;
    return true;
  }
}
