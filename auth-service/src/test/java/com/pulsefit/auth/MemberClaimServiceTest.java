package com.pulsefit.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
import com.pulsefit.auth.service.AuditLogService;
import com.pulsefit.auth.service.MemberClaimService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class MemberClaimServiceTest {

  @Mock private ClaimTokenRepository claimTokenRepository;
  @Mock private UserRepository userRepository;
  @Mock private MemberServiceClient memberServiceClient;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private com.pulsefit.auth.repository.AuditLogRepository auditLogRepository;

  private JwtTokenProvider jwtTokenProvider;
  private AuditLogService auditLogService;
  private MemberClaimService memberClaimService;

  @BeforeEach
  void setUp() {
    jwtTokenProvider = new JwtTokenProvider(new org.springframework.core.io.DefaultResourceLoader());
    org.springframework.test.util.ReflectionTestUtils.setField(jwtTokenProvider, "privateKeyPath", "classpath:jwt-private-test.pem");
    org.springframework.test.util.ReflectionTestUtils.setField(jwtTokenProvider, "publicKeyPath", "classpath:jwt-public.pem");
    org.springframework.test.util.ReflectionTestUtils.setField(jwtTokenProvider, "issuer", "pulsefit-auth-service");
    org.springframework.test.util.ReflectionTestUtils.setField(jwtTokenProvider, "audience", "pulsefit-api");
    org.springframework.test.util.ReflectionTestUtils.setField(jwtTokenProvider, "expirationMinutes", 15L);
    jwtTokenProvider.init();

    auditLogService = new AuditLogService(auditLogRepository);
    memberClaimService =
        new MemberClaimService(
            claimTokenRepository,
            userRepository,
            memberServiceClient,
            passwordEncoder,
            jwtTokenProvider,
            auditLogService);
  }

  @Test
  void initiateClaim_Success() {
    ClaimInitiateRequest req = new ClaimInitiateRequest("member@pulsefit.com");
    MemberDto member =
        new MemberDto(25L, "Member Name", "member@pulsefit.com", "12345", LocalDate.of(1995, 5, 5), "ACTIVE", OffsetDateTime.now());

    when(userRepository.existsByEmail("member@pulsefit.com")).thenReturn(false);
    when(memberServiceClient.findByEmail("member@pulsefit.com")).thenReturn(member);
    when(claimTokenRepository.findByEmailOrderByCreatedAtDesc("member@pulsefit.com")).thenReturn(Collections.emptyList());
    when(passwordEncoder.encode(anyString())).thenReturn("hashed_otp");

    ClaimInitiateResponse resp = memberClaimService.initiateClaim(req, "127.0.0.1");
    assertNotNull(resp);
    assertNotNull(resp.devOtp());
    verify(claimTokenRepository, times(1)).save(any(ClaimToken.class));
  }

  @Test
  void completeClaim_Success() {
    ClaimCompleteRequest req = new ClaimCompleteRequest("member@pulsefit.com", "123456", "NewPassword123!");
    ClaimToken token = new ClaimToken("member@pulsefit.com", 25L, "hashed_otp", LocalDateTime.now().plusMinutes(10), 3);
    MemberDto member =
        new MemberDto(25L, "Sarah Connor", "member@pulsefit.com", "12345", LocalDate.of(1995, 5, 5), "ACTIVE", OffsetDateTime.now());

    when(userRepository.existsByEmail("member@pulsefit.com")).thenReturn(false);
    when(claimTokenRepository.findTopByEmailAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            eq("member@pulsefit.com"), any(LocalDateTime.class)))
        .thenReturn(Optional.of(token));
    when(passwordEncoder.matches("123456", "hashed_otp")).thenReturn(true);
    when(memberServiceClient.findByEmail("member@pulsefit.com")).thenReturn(member);
    when(passwordEncoder.encode("NewPassword123!")).thenReturn("hashed_new_pw");
    when(userRepository.save(any(User.class))).thenAnswer(inv -> {
      User u = inv.getArgument(0);
      u.setId(105L);
      return u;
    });

    AuthResponse resp = memberClaimService.completeClaim(req, "127.0.0.1");
    assertNotNull(resp);
    assertNotNull(resp.accessToken());
    assertEquals(25L, resp.user().memberId());
    assertTrue(token.isConsumed());
  }

  @Test
  void completeClaim_InvalidOtp_IncrementsAttempts() {
    ClaimCompleteRequest req = new ClaimCompleteRequest("member@pulsefit.com", "000000", "NewPassword123!");
    ClaimToken token = new ClaimToken("member@pulsefit.com", 25L, "hashed_otp", LocalDateTime.now().plusMinutes(10), 3);

    when(userRepository.existsByEmail("member@pulsefit.com")).thenReturn(false);
    when(claimTokenRepository.findTopByEmailAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            eq("member@pulsefit.com"), any(LocalDateTime.class)))
        .thenReturn(Optional.of(token));
    when(passwordEncoder.matches("000000", "hashed_otp")).thenReturn(false);

    assertThrows(BadRequestException.class, () -> memberClaimService.completeClaim(req, "127.0.0.1"));
    assertEquals(1, token.getAttemptsCount());
    assertFalse(token.isConsumed());
  }
}

