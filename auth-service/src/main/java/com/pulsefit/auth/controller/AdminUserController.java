package com.pulsefit.auth.controller;

import com.pulsefit.auth.dto.*;
import com.pulsefit.auth.service.AdminUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

  private final AdminUserService adminUserService;

  public AdminUserController(AdminUserService adminUserService) {
    this.adminUserService = adminUserService;
  }

  @PostMapping("/staff")
  @ResponseStatus(HttpStatus.CREATED)
  public UserSummary createStaff(
      @Valid @RequestBody StaffCreateRequest request,
      Authentication authentication,
      HttpServletRequest httpRequest) {
    Long actorId = getActorId(authentication);
    String actorEmail = authentication != null ? authentication.getName() : "system";
    return adminUserService.createStaff(request, actorId, actorEmail, getClientIp(httpRequest));
  }

  @PostMapping("/admin")
  @ResponseStatus(HttpStatus.CREATED)
  public UserSummary createAdmin(
      @Valid @RequestBody AdminCreateRequest request,
      Authentication authentication,
      HttpServletRequest httpRequest) {
    Long actorId = getActorId(authentication);
    String actorEmail = authentication != null ? authentication.getName() : "system";
    return adminUserService.createAdmin(request, actorId, actorEmail, getClientIp(httpRequest));
  }

  @PutMapping("/{id}/role")
  public UserSummary updateRole(
      @PathVariable Long id,
      @Valid @RequestBody RoleUpdateRequest request,
      Authentication authentication,
      HttpServletRequest httpRequest) {
    Long actorId = getActorId(authentication);
    String actorEmail = authentication != null ? authentication.getName() : "system";
    return adminUserService.updateUserRole(id, request, actorId, actorEmail, getClientIp(httpRequest));
  }

  @PutMapping("/{id}/status")
  public UserSummary updateStatus(
      @PathVariable Long id,
      @Valid @RequestBody StatusUpdateRequest request,
      Authentication authentication,
      HttpServletRequest httpRequest) {
    Long actorId = getActorId(authentication);
    String actorEmail = authentication != null ? authentication.getName() : "system";
    return adminUserService.updateUserStatus(id, request, actorId, actorEmail, getClientIp(httpRequest));
  }

  private Long getActorId(Authentication authentication) {
    if (authentication != null && authentication.getPrincipal() instanceof Long userId) {
      return userId;
    }
    return null;
  }

  private String getClientIp(HttpServletRequest request) {
    String xf = request.getHeader("X-Forwarded-For");
    if (xf != null && !xf.isBlank()) {
      return xf.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}

