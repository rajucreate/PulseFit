package com.pulsefit.auth.controller;

import com.pulsefit.auth.dto.InvitationDtos.AcceptInvitationRequest;
import com.pulsefit.auth.dto.InvitationDtos.InvitationPreview;
import com.pulsefit.auth.dto.InvitationDtos.InvitationView;
import com.pulsefit.auth.dto.InvitationDtos.InviteRequest;
import com.pulsefit.auth.dto.InvitationDtos.MessageResponse;
import com.pulsefit.auth.service.InvitationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/invitations")
public class InvitationController {
  private final InvitationService service;

  public InvitationController(InvitationService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  public InvitationView create(@Valid @RequestBody InviteRequest request) {
    return service.create(request);
  }

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  public List<InvitationView> list() {
    return service.list();
  }

  @PostMapping("/{id}/resend")
  @PreAuthorize("hasRole('ADMIN')")
  public InvitationView resend(@PathVariable Long id) {
    return service.resend(id);
  }

  @GetMapping("/preview")
  public InvitationPreview preview(@RequestParam String token) {
    return service.preview(token);
  }

  @PostMapping("/accept")
  public MessageResponse accept(@Valid @RequestBody AcceptInvitationRequest request) {
    return service.accept(request);
  }
}
