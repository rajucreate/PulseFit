package com.pulsefit.attendance.controller;

import com.pulsefit.attendance.client.TraineeAssignmentClient;
import com.pulsefit.attendance.client.TraineeAssignmentClient.AssignmentCheck;
import com.pulsefit.attendance.dto.*;
import com.pulsefit.attendance.security.AuthenticatedUser;
import com.pulsefit.attendance.service.AttendanceService;
import feign.FeignException;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {
  private final AttendanceService service;
  private final TraineeAssignmentClient assignments;

  public AttendanceController(AttendanceService service, TraineeAssignmentClient assignments) {
    this.service = service;
    this.assignments = assignments;
  }

  @PostMapping("/check-in")
  @ResponseStatus(HttpStatus.CREATED)
  public AttendanceResponse checkIn(
      @Valid @RequestBody CheckInRequest r, Authentication authentication) {
    if (authentication != null
        && authentication.getPrincipal() instanceof AuthenticatedUser user
        && "TRAINEE".equalsIgnoreCase(user.role())) {
      throw new AccessDeniedException("Trainees cannot create or change attendance records.");
    }
    enforceMemberIdOwnership(r.memberId(), authentication, "check in for");
    return service.checkIn(r);
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  public List<AttendanceResponse> all() {
    return service.all();
  }

  @GetMapping("/{id}")
  public AttendanceResponse get(@PathVariable Long id, Authentication authentication) {
    AttendanceResponse resp = service.get(id);
    enforceMemberIdOwnership(resp.memberId(), authentication, "access attendance record of");
    return resp;
  }

  @GetMapping("/member/{memberId}")
  public List<AttendanceResponse> byMember(
      @PathVariable Long memberId, Authentication authentication) {
    enforceMemberIdOwnership(memberId, authentication, "view attendance records for");
    return service.byMember(memberId);
  }

  private void enforceMemberIdOwnership(
      Long targetMemberId, Authentication authentication, String action) {
    if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
      if ("TRAINEE".equalsIgnoreCase(user.role())) {
        if (user.email() == null || user.email().isBlank() || assignments == null) {
          throw new AccessDeniedException("This member is not assigned to you.");
        }
        try {
          AssignmentCheck check = assignments.assigned(user.email(), targetMemberId);
          if (check == null || !check.assigned()) {
            throw new AccessDeniedException("This member is not assigned to you.");
          }
        } catch (FeignException ex) {
          throw new AccessDeniedException("This member is not assigned to you.");
        }
        return;
      }
      if ("MEMBER".equalsIgnoreCase(user.role())) {
        if (user.memberId() == null || !user.memberId().equals(targetMemberId)) {
          throw new AccessDeniedException(
              "Access denied: You can only " + action + " your own member ID (" + user.memberId() + ").");
        }
      }
    }
  }
}
