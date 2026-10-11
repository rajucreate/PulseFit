package com.pulsefit.payment.client;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

public class Clients {
  @FeignClient(name = "SUBSCRIPTION-SERVICE")
  public interface SubscriptionClient {
    @GetMapping("/api/subscriptions/{id}")
    SubscriptionView get(@PathVariable("id") Long id);

    @PostMapping("/api/subscriptions/internal/{id}/activate")
    SubscriptionView activate(@PathVariable("id") Long id);

    @PostMapping("/api/subscriptions/internal/checkout")
    SubscriptionView prepare(CheckoutCommand command);

    @PostMapping("/api/subscriptions/internal/{id}/cancel")
    SubscriptionView cancel(@PathVariable("id") Long id);
  }

  @FeignClient(name = "SUBSCRIPTION-SERVICE", contextId = "planClient")
  public interface PlanClient {
    @GetMapping("/api/plans/{id}")
    PlanView get(@PathVariable("id") Long id);
  }

  @FeignClient(name = "MEMBER-SERVICE")
  public interface MemberClient {
    @GetMapping("/api/members/{id}")
    MemberView get(@PathVariable("id") Long id);
  }

  @FeignClient(name = "NOTIFICATION-SERVICE")
  public interface NotificationClient {
    @PostMapping("/api/notifications/internal")
    void create(NotificationRequest request);
  }

  public record CheckoutCommand(Long memberId, Long planId) {}

  public record SubscriptionView(
      Long id, Long memberId, Long planId, LocalDate startDate, LocalDate expiryDate, String status) {}

  public record PlanView(
      Long id,
      String planName,
      Integer durationInDays,
      BigDecimal price,
      String description,
      boolean active) {}

  public record MemberView(Long id, String name, String email, String contact, String status) {}

  public record NotificationRequest(
      Long memberId, String type, String recipient, String subject, String message) {}
}
