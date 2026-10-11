package com.pulsefit.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class InvitationDtos {
  public record InviteRequest(
      @NotBlank @Size(max = 120) String fullName,
      @NotBlank @Email String email) {}

  public record AcceptInvitationRequest(
      @NotBlank String token,
      @NotBlank @Size(min = 8, message = "Password must be at least 8 characters long") String password) {}

  public record InvitationView(
      Long id,
      String email,
      String fullName,
      String status,
      String deliveryStatus,
      String message,
      String inviteUrl) {}

  public record InvitationPreview(boolean valid, String reason, String email, String fullName) {}

  public record MessageResponse(String message) {}

  public record ForgotPasswordRequest(@NotBlank @Email String email) {}

  public record ResetPasswordRequest(
      @NotBlank String token,
      @NotBlank @Size(min = 8, message = "Password must be at least 8 characters long") String password) {}
}
