package com.pulsefit.reporting.service;

import com.pulsefit.reporting.client.ReportClients.AttendanceClient;
import com.pulsefit.reporting.client.ReportClients.AttendanceView;
import com.pulsefit.reporting.client.ReportClients.FacilityClient;
import com.pulsefit.reporting.client.ReportClients.FacilityView;
import com.pulsefit.reporting.client.ReportClients.MemberClient;
import com.pulsefit.reporting.client.ReportClients.MemberView;
import com.pulsefit.reporting.client.ReportClients.PaymentClient;
import com.pulsefit.reporting.client.ReportClients.PaymentView;
import com.pulsefit.reporting.client.ReportClients.SubscriptionClient;
import com.pulsefit.reporting.client.ReportClients.SubscriptionView;
import com.pulsefit.reporting.client.ReportClients.TrainerClient;
import com.pulsefit.reporting.client.ReportClients.TrainerView;
import feign.FeignException;
import java.time.LocalDate;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
public class ReportService {
  private final MemberClient members;
  private final SubscriptionClient subscriptions;
  private final AttendanceClient attendance;
  private final PaymentClient payments;
  private final FacilityClient facilities;
  private final TrainerClient trainers;

  public ReportService(
      MemberClient members,
      SubscriptionClient subscriptions,
      AttendanceClient attendance,
      PaymentClient payments,
      FacilityClient facilities,
      TrainerClient trainers) {
    this.members = members;
    this.subscriptions = subscriptions;
    this.attendance = attendance;
    this.payments = payments;
    this.facilities = facilities;
    this.trainers = trainers;
  }

  public Map<String, Object> dashboard(LocalDate from, LocalDate to, Long facilityId) {
    return Map.of(
        "members", memberReport(),
        "subscriptions", subscriptionReport(),
        "attendance", attendanceReport(from, to, facilityId),
        "revenue", revenueReport(from, to),
        "facilities", facilityReport(),
        "trainers", trainerReport());
  }

  public Map<String, Object> memberReport() {
    return section(() -> ReportMath.members(members.all()));
  }

  public Map<String, Object> subscriptionReport() {
    return section(() -> ReportMath.subscriptions(subscriptions.all()));
  }

  public Map<String, Object> attendanceReport(LocalDate from, LocalDate to, Long facilityId) {
    return section(() -> ReportMath.attendance(attendance.all(), from, to, facilityId));
  }

  public Map<String, Object> revenueReport(LocalDate from, LocalDate to) {
    return section(() -> ReportMath.revenue(payments.all(), from, to));
  }

  public Map<String, Object> facilityReport() {
    return section(() -> ReportMath.facilities(facilities.all()));
  }

  public Map<String, Object> paymentReport(LocalDate from, LocalDate to) {
    return revenueReport(from, to);
  }

  public Map<String, Object> trainerReport() {
    return section(() -> ReportMath.trainers(trainers.all()));
  }

  private Map<String, Object> section(Supplier<Map<String, Object>> loader) {
    try {
      return Map.of("available", true, "data", loader.get());
    } catch (FeignException ex) {
      return Map.of("available", false, "error", "Upstream service is unavailable");
    } catch (RuntimeException ex) {
      return Map.of("available", false, "error", "Report data could not be loaded");
    }
  }
}
