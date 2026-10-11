package com.pulsefit.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.pulsefit.auth.dto.InvitationDtos.ForgotPasswordRequest;
import com.pulsefit.auth.dto.InvitationDtos.ResetPasswordRequest;
import com.pulsefit.auth.exception.BadRequestException;
import com.pulsefit.auth.model.AccountToken;
import com.pulsefit.auth.model.Role;
import com.pulsefit.auth.model.User;
import com.pulsefit.auth.repository.AccountTokenRepository;
import com.pulsefit.auth.repository.UserRepository;
import com.pulsefit.auth.service.ClubMailer;
import com.pulsefit.auth.service.PasswordResetService;
import com.pulsefit.auth.service.TokenHasher;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {
  @Mock private AccountTokenRepository tokens;
  @Mock private UserRepository users;
  @Mock private ClubMailer mailer;
  private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
  private PasswordResetService service;

  @BeforeEach
  void setUp() {
    service = new PasswordResetService(tokens, users, encoder, mailer, "http://localhost:5173", 30);
  }

  @Test
  void unknownEmailGetsTheSameResponse() {
    when(users.findByEmailIgnoreCase("missing@pulsefit.com")).thenReturn(Optional.empty());

    var response = service.request(new ForgotPasswordRequest("missing@pulsefit.com"));

    assertEquals(PasswordResetService.GENERIC, response.message());
    verify(tokens, never()).save(any());
  }

  @Test
  void knownEmailAlsoGetsTheGenericResponse() {
    User user = member();
    when(users.findByEmailIgnoreCase("ada@pulsefit.com")).thenReturn(Optional.of(user));
    when(tokens.countByEmailAndPurposeAndCreatedAtAfter(any(), any(), any())).thenReturn(0L);
    when(mailer.configured()).thenReturn(false);

    var response = service.request(new ForgotPasswordRequest("Ada@PulseFit.com"));

    assertEquals(PasswordResetService.GENERIC, response.message());
    verify(tokens).save(any(AccountToken.class));
  }

  @Test
  void validTokenUpdatesThePasswordWithTheExistingEncoder() {
    User user = member();
    AccountToken token = resetToken("reset-token");
    when(tokens.findByTokenHash(TokenHasher.sha256("reset-token"))).thenReturn(Optional.of(token));
    when(tokens.consumeIfOpen(eq(7L), any())).thenReturn(1);
    when(users.findByEmailIgnoreCase("ada@pulsefit.com")).thenReturn(Optional.of(user));
    when(users.save(user)).thenReturn(user);

    service.reset(new ResetPasswordRequest("reset-token", "NewPassword1"));

    assertTrue(encoder.matches("NewPassword1", user.getPasswordHash()));
    assertEquals(2L, user.getTokenVersion());
  }

  @Test
  void consumedTokenIsRejected() {
    AccountToken token = resetToken("reset-token");
    when(tokens.findByTokenHash(any())).thenReturn(Optional.of(token));
    when(tokens.consumeIfOpen(eq(7L), any())).thenReturn(0);

    assertThrows(
        BadRequestException.class, () -> service.reset(new ResetPasswordRequest("reset-token", "NewPassword1")));
  }

  @Test
  void expiredTokenIsRejected() {
    AccountToken token = resetToken("reset-token");
    ReflectionTestUtils.setField(token, "expiresAt", LocalDateTime.now().minusMinutes(1));
    when(tokens.findByTokenHash(any())).thenReturn(Optional.of(token));

    assertThrows(
        BadRequestException.class, () -> service.reset(new ResetPasswordRequest("reset-token", "NewPassword1")));
  }

  private User member() {
    User user = new User("ada@pulsefit.com", encoder.encode("OldPassword1"), "Ada", "Lovelace", Role.MEMBER, 8L);
    user.setId(3L);
    return user;
  }

  private AccountToken resetToken(String raw) {
    AccountToken token =
        new AccountToken(
            "ada@pulsefit.com",
            "Ada Lovelace",
            TokenHasher.sha256(raw),
            AccountToken.Purpose.RESET,
            LocalDateTime.now().plusMinutes(20));
    ReflectionTestUtils.setField(token, "id", 7L);
    return token;
  }
}
