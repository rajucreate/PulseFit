package com.pulsefit.member.controller;

import com.pulsefit.member.dto.*;
import com.pulsefit.member.security.AuthenticatedUser;
import com.pulsefit.member.service.MemberService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/members")
public class MemberController {
  private final MemberService service;

  public MemberController(MemberService service) {
    this.service = service;
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  public MemberResponse create(@Valid @RequestBody MemberRequest request) {
    return service.create(request);
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  public List<MemberResponse> findAll() {
    return service.findAll();
  }

  @GetMapping("/{id}")
  public MemberResponse findById(@PathVariable Long id, Authentication authentication) {
    enforceOwnership(id, authentication, "access");
    return service.findById(id);
  }

  @PutMapping("/{id}")
  public MemberResponse update(
      @PathVariable Long id,
      @Valid @RequestBody MemberRequest request,
      Authentication authentication) {
    enforceOwnership(id, authentication, "update");
    return service.update(id, request);
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    service.delete(id);
  }

  private void enforceOwnership(Long targetMemberId, Authentication authentication, String action) {
    if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
      if ("MEMBER".equalsIgnoreCase(user.role())) {
        if (user.memberId() == null || !user.memberId().equals(targetMemberId)) {
          throw new AccessDeniedException(
              "Access denied: You can only " + action + " your own member profile (ID: " + user.memberId() + ").");
        }
      }
    }
  }
}
