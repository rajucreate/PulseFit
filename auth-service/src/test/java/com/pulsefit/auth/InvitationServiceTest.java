package com.pulsefit.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.pulsefit.auth.client.MemberDto;
import com.pulsefit.auth.client.MemberServiceClient;
import com.pulsefit.auth.dto.InvitationDtos.AcceptInvitationRequest;
import com.pulsefit.auth.dto.InvitationDtos.InviteRequest;
import com.pulsefit.auth.exception.BadRequestException;
import com.pulsefit.auth.exception.ConflictException;
import com.pulsefit.auth.model.AccountToken;
import com.pulsefit.auth.model.Role;
import com.pulsefit.auth.model.User;
import com.pulsefit.auth.repository.AccountTokenRepository;
import com.pulsefit.auth.repository.UserRepository;
import com.pulsefit.auth.service.ClubMailer;
import com.pulsefit.auth.service.InvitationService;
import com.pulsefit.auth.service.TokenHasher;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {
  @Mock private AccountTokenRepository tokens;
  @Mock private UserRepository users;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private MemberServiceClient members;
  @Mock private ClubMailer mailer;
  private InvitationService service;

  @BeforeEach
  void setUp() {
    service = new InvitationService(tokens, users, passwordEncoder, members, mailer, "http://localhost:5173", 24);
  }

  @Test
  void adminInviteIsSavedAndDoesNotPretendEmailWasSent() {
    when(users.findByEmailIgnoreCase("ada@pulsefit.com")).thenReturn(Optional.empty());
    when(tokens.findFirstByEmailAndPurposeAndConsumedAtIsNullAndExpiresAtAfter(any(), any(), any()))
        .thenReturn(Optional.empty());
    when(tokens.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0), 4L));
    when(mailer.configured()).thenReturn(false);

    var view = service.create(new InviteRequest("Ada Lovelace", "Ada@PulseFit.com"));

    assertEquals("ada@pulsefit.com", view.email());
    assertEquals("NOT_CONFIGURED", view.deliveryStatus());
    assertTrue(view.inviteUrl().startsWith("http://localhost:5173/invite/"));
    verify(mailer, never()).send(any(), any(), any());
  }

  @Test
  void duplicateEmailIsRejected() {
    when(users.findByEmailIgnoreCase("ada@pulsefit.com"))
        .thenReturn(Optional.of(new User("ada@pulsefit.com", "hash", "Ada", "Lovelace", Role.MEMBER, 1L)));

    assertThrows(ConflictException.class, () -> service.create(new InviteRequest("Ada", "ada@pulsefit.com")));
  }

  @Test
  void emailFailureIsReported() {
    when(users.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
    when(tokens.findFirstByEmailAndPurposeAndConsumedAtIsNullAndExpiresAtAfter(any(), any(), any()))
        .thenReturn(Optional.empty());
    when(tokens.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0), 4L));
    when(mailer.configured()).thenReturn(true);
    doThrow(new IllegalStateException("smtp down")).when(mailer).send(any(), any(), any());

    var view = service.create(new InviteRequest("Ada Lovelace", "ada@pulsefit.com"));

    assertEquals("FAILED", view.deliveryStatus());
    assertNull(view.inviteUrl());
  }

  @Test
  void validInvitationSetsAMemberPassword() {
    AccountToken token = openInvite("secret-token");
    when(tokens.findByTokenHash(TokenHasher.sha256("secret-token"))).thenReturn(Optional.of(token));
    when(tokens.consumeIfOpen(eq(4L), any())).thenReturn(1);
    when(users.findByEmailIgnoreCase("ada@pulsefit.com")).thenReturn(Optional.empty());
    when(members.findByEmail("ada@pulsefit.com")).thenReturn(member());
    when(passwordEncoder.encode("Password123!")).thenReturn("hashed");
    when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    service.accept(new AcceptInvitationRequest("secret-token", "Password123!"));

    verify(users).save(argThat(user -> user.getRole() == Role.MEMBER && "hashed".equals(user.getPasswordHash())));
  }

  @Test
  void reusedInvitationIsRejected() {
    AccountToken token = openInvite("secret-token");
    when(tokens.findByTokenHash(any())).thenReturn(Optional.of(token));
    when(tokens.consumeIfOpen(eq(4L), any())).thenReturn(0);

    assertThrows(
        BadRequestException.class,
        () -> service.accept(new AcceptInvitationRequest("secret-token", "Password123!")));
    verify(users, never()).save(any());
  }

  @Test
  void expiredInvitationIsRejected() {
    AccountToken token = openInvite("secret-token");
    ReflectionTestUtils.setField(token, "expiresAt", LocalDateTime.now().minusMinutes(1));
    when(tokens.findByTokenHash(any())).thenReturn(Optional.of(token));

    assertThrows(
        BadRequestException.class,
        () -> service.accept(new AcceptInvitationRequest("secret-token", "Password123!")));
  }

  private AccountToken openInvite(String raw) {
    AccountToken token =
        new AccountToken(
            "ada@pulsefit.com",
            "Ada Lovelace",
            TokenHasher.sha256(raw),
            AccountToken.Purpose.INVITE,
            LocalDateTime.now().plusHours(1));
    return withId(token, 4L);
  }

  private MemberDto member() {
    return new MemberDto(8L, "Ada Lovelace", "ada@pulsefit.com", null, null, "ACTIVE", OffsetDateTime.now());
  }

  private static AccountToken withId(AccountToken token, Long id) {
    ReflectionTestUtils.setField(token, "id", id);
    return token;
  }
}
