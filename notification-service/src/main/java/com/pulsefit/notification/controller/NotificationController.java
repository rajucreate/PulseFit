package com.pulsefit.notification.controller;

import com.pulsefit.notification.dto.NotificationDtos.NotificationRequest;
import com.pulsefit.notification.dto.NotificationDtos.NotificationResponse;
import com.pulsefit.notification.model.NotificationStatus;
import com.pulsefit.notification.security.AuthenticatedUser;
import com.pulsefit.notification.service.NotificationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
  private final NotificationService service;

  public NotificationController(NotificationService service) {
    this.service = service;
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  public NotificationResponse create(@Valid @RequestBody NotificationRequest request) {
    return service.create(request);
  }

  @PostMapping("/internal")
  @ResponseStatus(HttpStatus.CREATED)
  public NotificationResponse createInternal(@Valid @RequestBody NotificationRequest request) {
    return service.create(request);
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  public List<NotificationResponse> search(
      @RequestParam(required = false) Long memberId,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) NotificationStatus status) {
    return service.search(memberId, type, status);
  }

  @GetMapping("/{id}")
  public NotificationResponse get(@PathVariable Long id, Authentication authentication) {
    NotificationResponse response = service.get(id);
    enforce(response.memberId(), authentication);
    return response;
  }

  @GetMapping("/member/{memberId}")
  public List<NotificationResponse> byMember(@PathVariable Long memberId, Authentication authentication) {
    enforce(memberId, authentication);
    return service.byMember(memberId);
  }

  @PostMapping("/{id}/retry")
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  public NotificationResponse retry(@PathVariable Long id) {
    return service.retry(id);
  }

  private void enforce(Long memberId, Authentication authentication) {
    if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
      if ("MEMBER".equalsIgnoreCase(user.role())
          && (user.memberId() == null || !user.memberId().equals(memberId))) {
        throw new AccessDeniedException("Access denied: you can only read your own notifications.");
      }
    }
  }
}
