package com.pulsefit.auth.service;

import com.pulsefit.auth.client.MemberServiceClient;
import com.pulsefit.auth.dto.*;
import com.pulsefit.auth.exception.*;
import com.pulsefit.auth.model.Role;
import com.pulsefit.auth.model.User;
import com.pulsefit.auth.repository.UserRepository;
import com.pulsefit.auth.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;
  private final MemberServiceClient memberServiceClient;
  private final AuditLogService auditLogService;

  public AuthService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenProvider jwtTokenProvider,
      MemberServiceClient memberServiceClient,
      AuditLogService auditLogService) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenProvider = jwtTokenProvider;
    this.memberServiceClient = memberServiceClient;
    this.auditLogService = auditLogService;
  }

  @Transactional
  public AuthResponse register(RegisterRequest request, String ipAddress) {
    auditLogService.log(
        null, request.email(), ipAddress, "REGISTER", null, request.email(),
        "Public registration is closed", "FAILURE", "Invitation required");
    throw new ForbiddenException(
        "Public registration is closed. Ask a club administrator for an invitation.");
  }

  @Transactional
  public AuthResponse login(LoginRequest request, String ipAddress) {
    User user =
        userRepository
            .findByEmail(request.email())
            .orElse(null);

    if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      auditLogService.log(
          null, request.email(), ipAddress, "LOGIN",
          user != null ? user.getId() : null, request.email(),
          "Invalid credentials login attempt", "FAILURE", "Invalid email or password");
      throw new UnauthorizedException("Invalid email or password");
    }

    if (!user.isEnabled()) {
      auditLogService.log(
          user.getId(), user.getEmail(), ipAddress, "LOGIN",
          user.getId(), user.getEmail(),
          "Disabled account login attempt", "FAILURE", "Account disabled");
      throw new ForbiddenException("Account is disabled. Please contact administrator.");
    }

    String token = jwtTokenProvider.generateToken(user);
    auditLogService.log(
        user.getId(), user.getEmail(), ipAddress, "LOGIN",
        user.getId(), user.getEmail(),
        "User logged in successfully", "SUCCESS", null);

    return new AuthResponse(
        token, "Bearer", jwtTokenProvider.getExpirationSeconds(), toSummary(user));
  }

  @Transactional
  public void logout(Long userId, String email, String ipAddress) {
    if (userId == null) return;
    userRepository.incrementTokenVersion(userId);
    auditLogService.log(
        userId, email, ipAddress, "LOGOUT",
        userId, email,
        "Token invalidated / logged out", "SUCCESS", null);
  }

  @Transactional(readOnly = true)
  public UserSummary getCurrentUser(Long userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    return toSummary(user);
  }

  public UserSummary toSummary(User user) {
    return new UserSummary(
        user.getId(),
        user.getEmail(),
        user.getFirstName(),
        user.getLastName(),
        user.getRole(),
        user.getMemberId(),
        user.isEnabled(),
        user.getTokenVersion());
  }
}

