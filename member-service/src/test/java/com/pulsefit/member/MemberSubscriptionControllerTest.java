package com.pulsefit.member;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.pulsefit.member.client.SubscriptionClient;
import com.pulsefit.member.client.TraineeAssignmentClient;
import com.pulsefit.member.controller.MemberSubscriptionController;
import com.pulsefit.member.dto.AssignSubscriptionBody;
import com.pulsefit.member.dto.MemberResponse;
import com.pulsefit.member.dto.SubscriptionView;
import com.pulsefit.member.model.MemberStatus;
import com.pulsefit.member.security.AuthenticatedUser;
import com.pulsefit.member.service.MemberService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class MemberSubscriptionControllerTest {
  @Mock private MemberService members;
  @Mock private SubscriptionClient subscriptions;
  @Mock private TraineeAssignmentClient assignments;

  private MemberSubscriptionController controller;

  @BeforeEach
  void setUp() {
    controller = new MemberSubscriptionController(members, subscriptions, assignments);
  }

  @Test
  void listSubscriptionsForOwnMember_Succeeds() {
    AuthenticatedUser user = new AuthenticatedUser(10L, "ada@pulsefit.com", "MEMBER", 42L);
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            user, "token", Collections.singletonList(new SimpleGrantedAuthority("ROLE_MEMBER")));
    when(members.findById(42L))
        .thenReturn(
            new MemberResponse(
                42L, "Ada", "ada@pulsefit.com", "555", null, MemberStatus.ACTIVE, OffsetDateTime.now()));
    when(subscriptions.byMember(42L))
        .thenReturn(List.of(new SubscriptionView(1L, 42L, 2L, LocalDate.now(), LocalDate.now().plusDays(30), "PENDING")));

    List<SubscriptionView> rows = controller.list(42L, auth);
    assertEquals(1, rows.size());
  }

  @Test
  void listSubscriptionsForAnotherMember_Denied() {
    AuthenticatedUser user = new AuthenticatedUser(10L, "ada@pulsefit.com", "MEMBER", 42L);
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            user, "token", Collections.singletonList(new SimpleGrantedAuthority("ROLE_MEMBER")));
    when(members.findById(99L))
        .thenReturn(
            new MemberResponse(
                99L, "Other", "other@pulsefit.com", "555", null, MemberStatus.ACTIVE, OffsetDateTime.now()));

    assertThrows(AccessDeniedException.class, () -> controller.list(99L, auth));
    verify(subscriptions, never()).byMember(anyLong());
  }

  @Test
  void assignRejectsActiveStatus() {
    AuthenticatedUser user = new AuthenticatedUser(1L, "admin@pulsefit.com", "ADMIN", null);
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            user, "token", Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));
    when(members.findById(42L))
        .thenReturn(
            new MemberResponse(
                42L, "Ada", "ada@pulsefit.com", "555", null, MemberStatus.ACTIVE, OffsetDateTime.now()));

    AssignSubscriptionBody body =
        new AssignSubscriptionBody(2L, LocalDate.now(), LocalDate.now().plusDays(30), "ACTIVE");
    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> controller.assign(42L, body, auth));
    assertEquals(400, ex.getStatusCode().value());
    verify(subscriptions, never()).create(any());
  }
}
