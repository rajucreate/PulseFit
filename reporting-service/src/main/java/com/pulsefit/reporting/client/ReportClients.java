package com.pulsefit.reporting.client;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

public class ReportClients {
  @FeignClient(name = "MEMBER-SERVICE")
  public interface MemberClient {
    @GetMapping("/api/members")
    List<MemberView> all();
  }

  @FeignClient(name = "SUBSCRIPTION-SERVICE")
  public interface SubscriptionClient {
    @GetMapping("/api/subscriptions")
    List<SubscriptionView> all();
  }

  @FeignClient(name = "ATTENDANCE-SERVICE")
  public interface AttendanceClient {
    @GetMapping("/api/attendance")
    List<AttendanceView> all();
  }

  @FeignClient(name = "PAYMENT-SERVICE")
  public interface PaymentClient {
    @GetMapping("/api/payments")
    List<PaymentView> all();
  }

  @FeignClient(name = "FACILITY-SERVICE")
  public interface FacilityClient {
    @GetMapping("/api/facilities")
    List<FacilityView> all();
  }

  @FeignClient(name = "TRAINER-SERVICE")
  public interface TrainerClient {
    @GetMapping("/api/trainers")
    List<TrainerView> all();
  }

  public record MemberView(Long id, String status) {}

  public record SubscriptionView(Long id, String status, LocalDate startDate, LocalDate expiryDate) {}

  public record AttendanceView(
      Long id, Long memberId, Long facilityId, OffsetDateTime checkInTime, String accessStatus) {}

  public record PaymentView(
      Long id,
      BigDecimal amount,
      String transactionId,
      String paymentStatus,
      OffsetDateTime paymentDate) {}

  public record FacilityView(Long id, String name, String status) {}

  public record TrainerView(Long id, String status) {}
}
