package com.pulsefit.trainer.controller;

import com.pulsefit.trainer.dto.TrainerDtos.AssignmentResponse;
import com.pulsefit.trainer.dto.TrainerDtos.TrainerRequest;
import com.pulsefit.trainer.dto.TrainerDtos.TrainerResponse;
import com.pulsefit.trainer.model.TrainerStatus;
import com.pulsefit.trainer.security.AuthenticatedUser;
import com.pulsefit.trainer.service.TrainerService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trainers")
public class TrainerController {
  private final TrainerService service;

  public TrainerController(TrainerService service) {
    this.service = service;
  }

  @GetMapping
  public List<TrainerResponse> search(
      @RequestParam(required = false) Long facilityId,
      @RequestParam(required = false) String specialization,
      @RequestParam(required = false) TrainerStatus status) {
    return service.search(facilityId, specialization, status);
  }

  @GetMapping("/facility/{facilityId}")
  public List<TrainerResponse> byFacility(@PathVariable Long facilityId) {
    return service.byFacility(facilityId);
  }

  @GetMapping("/member/{memberId}")
  public List<AssignmentResponse> forMember(@PathVariable Long memberId, Authentication authentication) {
    enforceMember(memberId, authentication);
    return service.trainersOfMember(memberId);
  }

  @GetMapping("/{id}")
  public TrainerResponse get(@PathVariable Long id) {
    return service.get(id);
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  public TrainerResponse create(@Valid @RequestBody TrainerRequest request) {
    return service.create(request);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  public TrainerResponse update(@PathVariable Long id, @Valid @RequestBody TrainerRequest request) {
    return service.update(id, request);
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  public TrainerResponse deactivate(@PathVariable Long id) {
    return service.deactivate(id);
  }

  @PostMapping("/{trainerId}/members/{memberId}")
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  public AssignmentResponse assign(@PathVariable Long trainerId, @PathVariable Long memberId) {
    return service.assign(trainerId, memberId);
  }

  @DeleteMapping("/{trainerId}/members/{memberId}")
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unassign(@PathVariable Long trainerId, @PathVariable Long memberId) {
    service.unassign(trainerId, memberId);
  }

  @GetMapping("/{trainerId}/members")
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  public List<AssignmentResponse> members(@PathVariable Long trainerId) {
    return service.membersOf(trainerId);
  }

  private void enforceMember(Long memberId, Authentication authentication) {
    if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
      if ("MEMBER".equalsIgnoreCase(user.role())
          && (user.memberId() == null || !user.memberId().equals(memberId))) {
        throw new AccessDeniedException("Access denied: you can only view your own trainer.");
      }
    }
  }
}
