package com.pulsefit.auth.controller;

import com.pulsefit.auth.dto.InvitationDtos.ForgotPasswordRequest;
import com.pulsefit.auth.dto.InvitationDtos.MessageResponse;
import com.pulsefit.auth.dto.InvitationDtos.ResetPasswordRequest;
import com.pulsefit.auth.service.PasswordResetService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/password")
public class PasswordResetController {
  private final PasswordResetService service;

  public PasswordResetController(PasswordResetService service) {
    this.service = service;
  }

  @PostMapping("/forgot")
  public MessageResponse forgot(@Valid @RequestBody ForgotPasswordRequest request) {
    return service.request(request);
  }

  @PostMapping("/reset")
  public MessageResponse reset(@Valid @RequestBody ResetPasswordRequest request) {
    return service.reset(request);
  }
}
