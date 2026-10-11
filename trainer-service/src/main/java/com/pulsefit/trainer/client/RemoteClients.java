package com.pulsefit.trainer.client;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

public class RemoteClients {
  @FeignClient(name = "FACILITY-SERVICE")
  public interface FacilityClient {
    @GetMapping("/api/facilities/internal/{id}")
    FacilityView get(@PathVariable("id") Long id);
  }

  @FeignClient(name = "MEMBER-SERVICE")
  public interface MemberClient {
    @GetMapping("/api/members/{id}")
    MemberView get(@PathVariable("id") Long id);

    @GetMapping("/api/members/internal/{id}")
    MemberView internalGet(@PathVariable("id") Long id);

    @GetMapping("/api/members/internal/summaries")
    List<MemberView> summaries();
  }

  @FeignClient(name = "SUBSCRIPTION-SERVICE")
  public interface SubscriptionClient {
    @GetMapping("/api/plans")
    List<PlanView> plans();

    @GetMapping("/api/subscriptions/member/{memberId}")
    List<SubscriptionView> byMember(@PathVariable("memberId") Long memberId);
  }

  @FeignClient(name = "ATTENDANCE-SERVICE")
  public interface AttendanceClient {
    @GetMapping("/api/attendance/member/{memberId}")
    List<VisitView> byMember(@PathVariable("memberId") Long memberId);
  }

  public record FacilityView(Long id, String name, String status) {}

  public record MemberView(Long id, String name, String email, String contact, String status) {}

  public record PlanView(Long id, String planName, Integer durationInDays) {}

  public record SubscriptionView(
      Long id,
      Long memberId,
      Long planId,
      LocalDate startDate,
      LocalDate expiryDate,
      String status) {}

  public record VisitView(
      Long id,
      Long memberId,
      Long facilityId,
      OffsetDateTime checkInTime,
      String accessStatus,
      String denialReason) {}
}
