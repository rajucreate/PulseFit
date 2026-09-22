package com.pulsefit.auth.service;

import com.pulsefit.auth.client.MemberDto;
import com.pulsefit.auth.client.MemberServiceClient;
import com.pulsefit.auth.dto.*;
import com.pulsefit.auth.exception.*;
import com.pulsefit.auth.model.ClaimToken;
import com.pulsefit.auth.model.Role;
import com.pulsefit.auth.model.User;
import com.pulsefit.auth.repository.ClaimTokenRepository;
import com.pulsefit.auth.repository.UserRepository;
import com.pulsefit.auth.security.JwtTokenProvider;
import feign.FeignException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberClaimService {

  private final ClaimTokenRepository claimTokenRepository;
  private final UserRepository userRepository;
  private final MemberServiceClient memberServiceClient;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;
  private final AuditLogService auditLogService;
  private final SecureRandom secureRandom = new SecureRandom();

  public MemberClaimService(
      ClaimTokenRepository claimTokenRepository,
      UserRepository userRepository,
      MemberServiceClient memberServiceClient,
      PasswordEncoder passwordEncoder,
      JwtTokenProvider jwtTokenProvider,
      AuditLogService auditLogService) {
    this.claimTokenRepository = claimTokenRepository;
    this.userRepository = userRepository;
    this.memberServiceClient = memberServiceClient;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenProvider = jwtTokenProvider;
    this.auditLogService = auditLogService;
  }

  @Transactional
  public ClaimInitiateResponse initiateClaim(ClaimInitiateRequest request, String ipAddress) {
    if (userRepository.existsByEmail(request.email())) {
      auditLogService.log(
          null, request.email(), ipAddress, "CLAIM_INITIATE", null, request.email(),
          "Attempted claim on already registered email", "FAILURE", "Account already exists");
      throw new ConflictException("An active user account already exists for " + request.email() + ". Please login directly.");
    }

    MemberDto member;
    try {
      member = memberServiceClient.findByEmail(request.email());
    } catch (FeignException.NotFound e) {
      auditLogService.log(
          null, request.email(), ipAddress, "CLAIM_INITIATE", null, request.email(),
          "Member profile not found for claim", "FAILURE", "Member profile not found");
      throw new ResourceNotFoundException("No member profile found with email: " + request.email());
    }

    // Rate limiting: check recent unconsumed tokens
    var recentTokens = claimTokenRepository.findByEmailOrderByCreatedAtDesc(request.email());
    long activeInLast15Min = recentTokens.stream()
        .filter(t -> t.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(15)))
        .count();
    if (activeInLast15Min >= 3) {
      throw new BadRequestException("Too many verification attempts. Please wait 15 minutes before requesting another code.");
    }

    // Generate secure 6-digit OTP
    String rawOtp = String.format("%06d", secureRandom.nextInt(1000000));
    String tokenHash = passwordEncoder.encode(rawOtp);
    LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(15);

    ClaimToken claimToken = new ClaimToken(request.email(), member.id(), tokenHash, expiresAt, 3);
    claimTokenRepository.save(claimToken);

    auditLogService.log(
        null, request.email(), ipAddress, "CLAIM_INITIATE", null, request.email(),
        "Verification code generated for member ID: " + member.id(), "SUCCESS", null);

    // In non-production/dev environments, return devOtp for seamless testing without SMTP
    return new ClaimInitiateResponse(
        "Verification code sent to registered member email. Code expires in 15 minutes.",
        expiresAt,
        rawOtp);
  }

  @Transactional
  public AuthResponse completeClaim(ClaimCompleteRequest request, String ipAddress) {
    if (userRepository.existsByEmail(request.email())) {
      throw new ConflictException("User account already exists for " + request.email());
    }

    ClaimToken claimToken =
        claimTokenRepository
            .findTopByEmailAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                request.email(), LocalDateTime.now())
            .orElseThrow(
                () ->
                    new BadRequestException(
                        "No active verification code found or code has expired. Please initiate claim again."));

    if (claimToken.hasExceededAttempts()) {
      auditLogService.log(
          null, request.email(), ipAddress, "CLAIM_COMPLETE", null, request.email(),
          "Max verification attempts exceeded", "FAILURE", "Max attempts exceeded");
      throw new BadRequestException("Maximum verification attempts exceeded for this code. Please request a new code.");
    }

    if (!passwordEncoder.matches(request.otp(), claimToken.getTokenHash())) {
      claimToken.incrementAttempts();
      claimTokenRepository.save(claimToken);
      int remaining = claimToken.getMaxAttempts() - claimToken.getAttemptsCount();
      auditLogService.log(
          null, request.email(), ipAddress, "CLAIM_COMPLETE", null, request.email(),
          "Invalid OTP entered. Remaining attempts: " + remaining, "FAILURE", "Invalid OTP");
      throw new BadRequestException("Invalid verification code. Remaining attempts: " + remaining);
    }

    // Valid OTP - consume atomically
    claimToken.consume();
    claimTokenRepository.save(claimToken);

    MemberDto member = memberServiceClient.findByEmail(request.email());
    String[] parts = (member.name() != null ? member.name() : "Member User").split(" ", 2);
    String firstName = parts[0];
    String lastName = parts.length > 1 ? parts[1] : "";

    User newUser =
        new User(
            request.email(),
            passwordEncoder.encode(request.password()),
            firstName,
            lastName,
            Role.MEMBER,
            claimToken.getMemberId());

    User savedUser = userRepository.save(newUser);
    String token = jwtTokenProvider.generateToken(savedUser);

    auditLogService.log(
        savedUser.getId(), savedUser.getEmail(), ipAddress, "CLAIM_COMPLETE",
        savedUser.getId(), savedUser.getEmail(),
        "Existing member profile claimed and linked successfully. Member ID: " + claimToken.getMemberId(),
        "SUCCESS", null);

    return new AuthResponse(
        token,
        "Bearer",
        jwtTokenProvider.getExpirationSeconds(),
        new UserSummary(
            savedUser.getId(),
            savedUser.getEmail(),
            savedUser.getFirstName(),
            savedUser.getLastName(),
            savedUser.getRole(),
            savedUser.getMemberId(),
            savedUser.isEnabled(),
            savedUser.getTokenVersion()));
  }
}

