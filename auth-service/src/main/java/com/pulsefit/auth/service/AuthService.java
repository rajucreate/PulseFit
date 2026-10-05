package com.pulsefit.auth.service;

import com.pulsefit.auth.client.CreateMemberRequest;
import com.pulsefit.auth.client.MemberDto;
import com.pulsefit.auth.client.MemberServiceClient;
import com.pulsefit.auth.dto.*;
import com.pulsefit.auth.exception.*;
import com.pulsefit.auth.model.Role;
import com.pulsefit.auth.model.User;
import com.pulsefit.auth.repository.UserRepository;
import com.pulsefit.auth.security.JwtTokenProvider;
import feign.FeignException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
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
    if (userRepository.existsByEmail(request.email())) {
      auditLogService.log(
          null, request.email(), ipAddress, "REGISTER", null, request.email(),
          "Duplicate email registration attempt in auth", "FAILURE", "Email already registered");
      throw new ConflictException("Email already registered: " + request.email());
    }

    // Check if Member profile already exists at Member Service
    MemberDto existingMember = null;
    try {
      existingMember = memberServiceClient.findByEmail(request.email());
    } catch (FeignException.NotFound ignored) {
      // Not found is the expected normal case for brand new members
    } catch (Exception e) {
      // In case member service is unreachable or errors
      System.err.println("Member service lookup warning: " + e.getMessage());
    }

    if (existingMember != null) {
      auditLogService.log(
          null, request.email(), ipAddress, "REGISTER", null, request.email(),
          "Attempted public registration on existing member profile without claim OTP",
          "FAILURE", "Existing member claim required");
      throw new ConflictException(
          "A membership profile with email "
              + request.email()
              + " already exists at the front desk. Please use the member claim verification flow.");
    }

    // Provision new Member in member-service
    LocalDate dob = null;
    if (request.dateOfBirth() != null && !request.dateOfBirth().isBlank()) {
      try {
        dob = LocalDate.parse(request.dateOfBirth());
      } catch (DateTimeParseException ex) {
        throw new BadRequestException("dateOfBirth must be a valid date in yyyy-MM-dd format");
      }
    }

    String fullName = (request.firstName() + " " + request.lastName()).trim();
    MemberDto createdMember;
    try {
      createdMember =
          memberServiceClient.createMember(
              new CreateMemberRequest(fullName, request.email(), request.contact(), dob, "ACTIVE"));
    } catch (Exception e) {
      auditLogService.log(
          null, request.email(), ipAddress, "REGISTER", null, request.email(),
          "Failed to provision member in member-service", "FAILURE", e.getMessage());
      throw new BadRequestException("Failed to provision member profile: " + e.getMessage());
    }

    User newUser =
        new User(
            request.email(),
            passwordEncoder.encode(request.password()),
            request.firstName(),
            request.lastName(),
            Role.MEMBER,
            createdMember.id());

    User savedUser = userRepository.save(newUser);
    String token = jwtTokenProvider.generateToken(savedUser);

    auditLogService.log(
        savedUser.getId(), savedUser.getEmail(), ipAddress, "REGISTER",
        savedUser.getId(), savedUser.getEmail(),
        "New member registered successfully. Member ID: " + createdMember.id(), "SUCCESS", null);

    return new AuthResponse(
        token, "Bearer", jwtTokenProvider.getExpirationSeconds(), toSummary(savedUser));
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

