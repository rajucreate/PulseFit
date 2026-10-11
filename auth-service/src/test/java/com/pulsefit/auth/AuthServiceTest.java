package com.pulsefit.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.pulsefit.auth.client.CreateMemberRequest;
import com.pulsefit.auth.client.MemberDto;
import com.pulsefit.auth.client.MemberServiceClient;
import com.pulsefit.auth.dto.*;
import com.pulsefit.auth.exception.*;
import com.pulsefit.auth.model.Role;
import com.pulsefit.auth.model.User;
import com.pulsefit.auth.repository.UserRepository;
import com.pulsefit.auth.security.JwtTokenProvider;
import com.pulsefit.auth.service.AuditLogService;
import com.pulsefit.auth.service.AuthService;
import feign.FeignException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private MemberServiceClient memberServiceClient;
  @Mock private com.pulsefit.auth.repository.AuditLogRepository auditLogRepository;

  private JwtTokenProvider jwtTokenProvider;
  private AuditLogService auditLogService;
  private AuthService authService;

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
    authService =
        new AuthService(
            userRepository, passwordEncoder, jwtTokenProvider, memberServiceClient, auditLogService);
  }

  @Test
  void register_PublicSignupIsClosed() {
    RegisterRequest req =
        new RegisterRequest("new.user@pulsefit.com", "Password123!", "John", "Doe", "555-1234", "1990-01-01");

    assertThrows(ForbiddenException.class, () -> authService.register(req, "127.0.0.1"));
    verify(userRepository, never()).save(any());
  }

  @Test
  void login_Success() {
    LoginRequest req = new LoginRequest("user@pulsefit.com", "Password123!");
    User user = new User("user@pulsefit.com", "hashed_pw", "John", "Doe", Role.MEMBER, 50L);
    user.setId(10L);

    when(userRepository.findByEmail("user@pulsefit.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("Password123!", "hashed_pw")).thenReturn(true);

    AuthResponse resp = authService.login(req, "127.0.0.1");
    assertNotNull(resp);
    assertNotNull(resp.accessToken());
  }

  @Test
  void login_WrongPassword_ThrowsUnauthorized() {
    LoginRequest req = new LoginRequest("user@pulsefit.com", "WrongPassword");
    User user = new User("user@pulsefit.com", "hashed_pw", "John", "Doe", Role.MEMBER, 50L);

    when(userRepository.findByEmail("user@pulsefit.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("WrongPassword", "hashed_pw")).thenReturn(false);

    assertThrows(UnauthorizedException.class, () -> authService.login(req, "127.0.0.1"));
  }

  @Test
  void login_DisabledAccount_ThrowsForbidden() {
    LoginRequest req = new LoginRequest("disabled@pulsefit.com", "Password123!");
    User user = new User("disabled@pulsefit.com", "hashed_pw", "Disabled", "User", Role.MEMBER, 50L);
    user.setEnabled(false);

    when(userRepository.findByEmail("disabled@pulsefit.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("Password123!", "hashed_pw")).thenReturn(true);

    assertThrows(ForbiddenException.class, () -> authService.login(req, "127.0.0.1"));
  }

  @Test
  void logout_IncrementsTokenVersion() {
    authService.logout(10L, "user@pulsefit.com", "127.0.0.1");
    verify(userRepository, times(1)).incrementTokenVersion(10L);
  }
}

