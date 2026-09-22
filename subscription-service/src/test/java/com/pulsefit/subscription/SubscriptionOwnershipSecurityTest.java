package com.pulsefit.subscription;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pulsefit.subscription.client.MemberClient;
import com.pulsefit.subscription.controller.SubscriptionController;
import com.pulsefit.subscription.dto.SubscriptionRequest;
import com.pulsefit.subscription.dto.SubscriptionResponse;
import com.pulsefit.subscription.model.Subscription;
import com.pulsefit.subscription.model.SubscriptionStatus;
import com.pulsefit.subscription.repository.MembershipPlanRepository;
import com.pulsefit.subscription.repository.SubscriptionRepository;
import com.pulsefit.subscription.security.AuthenticatedUser;
import com.pulsefit.subscription.service.PlanService;
import com.pulsefit.subscription.service.SubscriptionService;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SubscriptionOwnershipSecurityTest {

  @Mock private SubscriptionRepository repository;
  @Mock private MembershipPlanRepository planRepository;
  @Mock private MemberClient memberClient;

  private SubscriptionService subscriptionService;
  private SubscriptionController controller;

  @BeforeEach
  void setUp() {
    subscriptionService = new SubscriptionService(repository, new PlanService(planRepository), memberClient);
    controller = new SubscriptionController(subscriptionService);
  }

  @Test
  void memberAccessingOwnSubscription_Succeeds() {
    AuthenticatedUser user = new AuthenticatedUser(10L, "alex@pulsefit.com", "MEMBER", 42L);
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            user, "token", Collections.singletonList(new SimpleGrantedAuthority("ROLE_MEMBER")));

    Subscription s =
        new Subscription(
            42L, 2L, LocalDate.now(), LocalDate.now().plusDays(30), SubscriptionStatus.ACTIVE);
    ReflectionTestUtils.setField(s, "id", 1L);
    when(repository.findById(1L)).thenReturn(Optional.of(s));

    SubscriptionResponse result = controller.get(1L, auth);
    assertNotNull(result);
    assertEquals(42L, result.memberId());
  }

  @Test
  void memberAccessingAnotherMemberSubscription_ThrowsAccessDenied() {
    AuthenticatedUser user = new AuthenticatedUser(10L, "alex@pulsefit.com", "MEMBER", 42L);
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            user, "token", Collections.singletonList(new SimpleGrantedAuthority("ROLE_MEMBER")));

    Subscription s =
        new Subscription(
            99L, 2L, LocalDate.now(), LocalDate.now().plusDays(30), SubscriptionStatus.ACTIVE);
    ReflectionTestUtils.setField(s, "id", 1L);
    when(repository.findById(1L)).thenReturn(Optional.of(s));

    assertThrows(AccessDeniedException.class, () -> controller.get(1L, auth));
  }

  @Test
  void memberQueryingOwnSubscriptionsList_Succeeds() {
    AuthenticatedUser user = new AuthenticatedUser(10L, "alex@pulsefit.com", "MEMBER", 42L);
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            user, "token", Collections.singletonList(new SimpleGrantedAuthority("ROLE_MEMBER")));

    when(repository.findByMemberId(42L)).thenReturn(Collections.emptyList());

    List<SubscriptionResponse> result = controller.byMember(42L, auth);
    assertNotNull(result);
  }

  @Test
  void memberQueryingAnotherMemberSubscriptionsList_ThrowsAccessDenied() {
    AuthenticatedUser user = new AuthenticatedUser(10L, "alex@pulsefit.com", "MEMBER", 42L);
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            user, "token", Collections.singletonList(new SimpleGrantedAuthority("ROLE_MEMBER")));

    assertThrows(AccessDeniedException.class, () -> controller.byMember(99L, auth));
    verify(repository, never()).findByMemberId(anyLong());
  }

  @Test
  void memberCreatingSubscriptionForAnotherMember_ThrowsAccessDenied() {
    AuthenticatedUser user = new AuthenticatedUser(10L, "alex@pulsefit.com", "MEMBER", 42L);
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            user, "token", Collections.singletonList(new SimpleGrantedAuthority("ROLE_MEMBER")));

    SubscriptionRequest req =
        new SubscriptionRequest(
            99L, 2L, LocalDate.now(), LocalDate.now().plusDays(30), SubscriptionStatus.ACTIVE);

    assertThrows(AccessDeniedException.class, () -> controller.create(req, auth));
    verify(repository, never()).save(any(Subscription.class));
  }
}

