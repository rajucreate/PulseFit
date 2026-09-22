package com.pulsefit.auth.controller;

import com.pulsefit.auth.dto.*;
import com.pulsefit.auth.service.AuthService;
import com.pulsefit.auth.service.MemberClaimService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService authService;
  private final MemberClaimService memberClaimService;

  public AuthController(AuthService authService, MemberClaimService memberClaimService) {
    this.authService = authService;
    this.memberClaimService = memberClaimService;
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public AuthResponse register(
      @Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
    return authService.register(request, getClientIp(httpRequest));
  }

  @PostMapping("/login")
  public AuthResponse login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
    return authService.login(request, getClientIp(httpRequest));
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(Authentication authentication, HttpServletRequest httpRequest) {
    if (authentication != null && authentication.getPrincipal() instanceof Long userId) {
      authService.logout(userId, authentication.getName(), getClientIp(httpRequest));
    }
  }

  @GetMapping("/me")
  public UserSummary me(Authentication authentication) {
    if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
      throw new IllegalArgumentException("User not authenticated");
    }
    return authService.getCurrentUser(userId);
  }

  @PostMapping("/claim/initiate")
  public ClaimInitiateResponse initiateClaim(
      @Valid @RequestBody ClaimInitiateRequest request, HttpServletRequest httpRequest) {
    return memberClaimService.initiateClaim(request, getClientIp(httpRequest));
  }

  @PostMapping("/claim/complete")
  @ResponseStatus(HttpStatus.CREATED)
  public AuthResponse completeClaim(
      @Valid @RequestBody ClaimCompleteRequest request, HttpServletRequest httpRequest) {
    return memberClaimService.completeClaim(request, getClientIp(httpRequest));
  }

  private String getClientIp(HttpServletRequest request) {
    String xf = request.getHeader("X-Forwarded-For");
    if (xf != null && !xf.isBlank()) {
      return xf.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}

