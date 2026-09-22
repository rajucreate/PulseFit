package com.pulsefit.subscription.controller;

import com.pulsefit.subscription.dto.*;
import com.pulsefit.subscription.security.AuthenticatedUser;
import com.pulsefit.subscription.service.SubscriptionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {
  private final SubscriptionService service;

  public SubscriptionController(SubscriptionService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public SubscriptionResponse create(
      @Valid @RequestBody SubscriptionRequest r, Authentication authentication) {
    enforceMemberIdOwnership(r.memberId(), authentication, "create subscription for");
    return service.create(r);
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  public List<SubscriptionResponse> all() {
    return service.all();
  }

  @GetMapping("/{id}")
  public SubscriptionResponse get(@PathVariable Long id, Authentication authentication) {
    SubscriptionResponse resp = service.get(id);
    enforceMemberIdOwnership(resp.memberId(), authentication, "access subscription of");
    return resp;
  }

  @GetMapping("/member/{memberId}")
  public List<SubscriptionResponse> byMember(
      @PathVariable Long memberId, Authentication authentication) {
    enforceMemberIdOwnership(memberId, authentication, "view subscriptions for");
    return service.byMember(memberId);
  }

  @GetMapping("/member/{memberId}/valid")
  public ValidityResponse valid(@PathVariable Long memberId) {
    return service.validity(memberId);
  }

  @PutMapping("/{id}")
  public SubscriptionResponse update(
      @PathVariable Long id,
      @Valid @RequestBody SubscriptionRequest r,
      Authentication authentication) {
    SubscriptionResponse existing = service.get(id);
    enforceMemberIdOwnership(existing.memberId(), authentication, "update subscription of");
    enforceMemberIdOwnership(r.memberId(), authentication, "change subscription member to");
    return service.update(id, r);
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    service.delete(id);
  }

  private void enforceMemberIdOwnership(
      Long targetMemberId, Authentication authentication, String action) {
    if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
      if ("MEMBER".equalsIgnoreCase(user.role())) {
        if (user.memberId() == null || !user.memberId().equals(targetMemberId)) {
          throw new AccessDeniedException(
              "Access denied: You can only " + action + " your own member ID (" + user.memberId() + ").");
        }
      }
    }
  }
}