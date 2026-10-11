package com.pulsefit.trainer.dto;

import com.pulsefit.trainer.model.TrainerStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public class TrainerDtos {
  public record TrainerRequest(
      @NotBlank String name,
      @NotBlank String specialization,
      @NotNull @PositiveOrZero Integer experienceYears,
      @NotNull @Positive Long facilityId,
      @NotBlank String availability,
      @NotNull TrainerStatus status) {}

  public record TrainerResponse(
      Long id,
      String name,
      String specialization,
      Integer experienceYears,
      Long facilityId,
      String availability,
      TrainerStatus status) {}

  public record AssignmentResponse(Long id, Long trainerId, Long memberId, OffsetDateTime assignedAt) {}

  public record SubscriptionView(
      Long id,
      Long planId,
      String planName,
      Integer durationInDays,
      LocalDate startDate,
      LocalDate expiryDate,
      String status) {}

  public record VisitView(
      Long id,
      OffsetDateTime checkInTime,
      Long facilityId,
      String accessStatus,
      String denialReason) {}

  public record RosterMember(
      Long memberId,
      String name,
      String email,
      String contact,
      String status,
      List<SubscriptionView> subscriptions,
      List<VisitView> attendance) {}

  public record RosterResponse(Long trainerId, String trainerName, List<RosterMember> members) {}
}
