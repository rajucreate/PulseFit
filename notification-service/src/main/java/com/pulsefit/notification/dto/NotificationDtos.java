package com.pulsefit.notification.dto;

import com.pulsefit.notification.model.NotificationStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.OffsetDateTime;

public class NotificationDtos {
  public record NotificationRequest(
      @NotNull @Positive Long memberId,
      @NotBlank String type,
      @NotBlank @Email String recipient,
      @NotBlank String subject,
      @NotBlank String message) {}

  public record NotificationResponse(
      Long id,
      Long memberId,
      String type,
      String recipient,
      String subject,
      String message,
      NotificationStatus status,
      OffsetDateTime createdAt,
      String deliveryNote,
      int attempts) {}
}
