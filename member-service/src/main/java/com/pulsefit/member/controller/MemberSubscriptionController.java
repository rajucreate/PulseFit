package com.pulsefit.member.controller;

import com.pulsefit.member.client.SubscriptionClient;
import com.pulsefit.member.client.TraineeAssignmentClient;
import com.pulsefit.member.client.TraineeAssignmentClient.AssignmentCheck;
import com.pulsefit.member.dto.AssignSubscriptionBody;
import com.pulsefit.member.dto.AssignSubscriptionRequest;
import com.pulsefit.member.dto.SubscriptionView;
import com.pulsefit.member.security.AuthenticatedUser;
import com.pulsefit.member.service.MemberService;
import feign.FeignException;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/members")
public class MemberSubscriptionController {
  private final MemberService members;
  private final SubscriptionClient subscriptions;
  private final TraineeAssignmentClient assignments;

  public MemberSubscriptionController(
      MemberService members, SubscriptionClient subscriptions, TraineeAssignmentClient assignments) {
    this.members = members;
    this.subscriptions = subscriptions;
    this.assignments = assignments;
  }

  @GetMapping("/{id}/subscriptions")
  public List<SubscriptionView> list(@PathVariable Long id, Authentication authentication) {
    members.findById(id);
    enforceRead(id, authentication);
    try {
      return subscriptions.byMember(id);
    } catch (FeignException.NotFound ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscriptions were not found for this member");
    } catch (FeignException ex) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Subscription service is unavailable");
    }
  }

  @PostMapping("/{id}/subscriptions")
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  public SubscriptionView assign(
      @PathVariable Long id,
      @Valid @RequestBody AssignSubscriptionBody body,
      Authentication authentication) {
    members.findById(id);
    LocalDate start = body.startDate() == null ? LocalDate.now() : body.startDate();
    LocalDate expiry = body.expiryDate() == null ? start.plusDays(30) : body.expiryDate();
    if (expiry.isBefore(start)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expiry date must be on or after the start date");
    }
    String status =
        body.status() == null || body.status().isBlank() ? "PENDING" : body.status().trim().toUpperCase();
    if ("ACTIVE".equals(status)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "A membership becomes active only after payment is verified. Use PENDING.");
    }
    try {
      return subscriptions.create(new AssignSubscriptionRequest(id, body.planId(), start, expiry, status));
    } catch (FeignException.BadRequest ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, extractMessage(ex, "Invalid subscription request"));
    } catch (FeignException.NotFound ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, extractMessage(ex, "Plan or member was not found"));
    } catch (FeignException.Conflict ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, extractMessage(ex, "Subscription conflict"));
    } catch (FeignException.Forbidden ex) {
      throw new AccessDeniedException(extractMessage(ex, "Not allowed to assign this subscription"));
    } catch (FeignException ex) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Subscription service is unavailable");
    }
  }

  private void enforceRead(Long memberId, Authentication authentication) {
    if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
      return;
    }
    if ("TRAINEE".equalsIgnoreCase(user.role())) {
      if (user.email() == null || user.email().isBlank()) {
        throw new AccessDeniedException("This member is not assigned to you.");
      }
      try {
        AssignmentCheck check = assignments.assigned(user.email(), memberId);
        if (check == null || !check.assigned()) {
          throw new AccessDeniedException("This member is not assigned to you.");
        }
      } catch (FeignException ex) {
        throw new AccessDeniedException("This member is not assigned to you.");
      }
      return;
    }
    if ("MEMBER".equalsIgnoreCase(user.role())
        && (user.memberId() == null || !user.memberId().equals(memberId))) {
      throw new AccessDeniedException(
          "Access denied: You can only view subscriptions for your own member profile.");
    }
  }

  private static String extractMessage(FeignException ex, String fallback) {
    String body = ex.contentUTF8();
    if (body == null || body.isBlank()) {
      return fallback;
    }
    int messageIdx = body.indexOf("\"message\"");
    if (messageIdx < 0) {
      return fallback;
    }
    int start = body.indexOf(':', messageIdx);
    int quote1 = body.indexOf('"', start + 1);
    int quote2 = body.indexOf('"', quote1 + 1);
    if (quote1 < 0 || quote2 < 0) {
      return fallback;
    }
    return body.substring(quote1 + 1, quote2);
  }
}
