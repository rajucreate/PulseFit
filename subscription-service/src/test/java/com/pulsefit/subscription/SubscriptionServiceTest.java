package com.pulsefit.subscription;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.pulsefit.subscription.client.MemberClient;
import com.pulsefit.subscription.client.MemberClientResponse;
import com.pulsefit.subscription.dto.SubscriptionRequest;
import com.pulsefit.subscription.dto.SubscriptionResponse;
import com.pulsefit.subscription.dto.ValidityResponse;
import com.pulsefit.subscription.exception.ConflictException;
import com.pulsefit.subscription.exception.ResourceNotFoundException;
import com.pulsefit.subscription.model.*;
import com.pulsefit.subscription.repository.MembershipPlanRepository;
import com.pulsefit.subscription.repository.SubscriptionRepository;
import com.pulsefit.subscription.service.*;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {
  @Mock SubscriptionRepository repository;
  @Mock MembershipPlanRepository planRepository;
  @Mock MemberClient memberClient;

  private SubscriptionService service() {
    return new SubscriptionService(repository, new PlanService(planRepository), memberClient);
  }

  @Test
  void validSubscriptionIsRecognized() {
    Subscription s =
        new Subscription(
            1L,
            2L,
            LocalDate.now().minusDays(1),
            LocalDate.now().plusDays(1),
            SubscriptionStatus.ACTIVE);
    when(repository.findByMemberId(1L)).thenReturn(List.of(s));
    ValidityResponse response = service().validity(1L);
    assertTrue(response.valid());
    assertEquals(s.getId(), response.subscriptionId());
    assertTrue(service().valid(1L));
  }

  @Test
  void expiredSubscriptionIsRejected() {
    Subscription s =
        new Subscription(
            1L,
            2L,
            LocalDate.now().minusDays(5),
            LocalDate.now().minusDays(1),
            SubscriptionStatus.ACTIVE);
    when(repository.findByMemberId(1L)).thenReturn(List.of(s));
    ValidityResponse response = service().validity(1L);
    assertFalse(response.valid());
    assertNull(response.subscriptionId());
    assertFalse(service().valid(1L));
  }

  @Test
  void multipleSubscriptionsSelectsFurthestExpiry() {
    Subscription s1 =
        new Subscription(
            1L,
            2L,
            LocalDate.now().minusDays(2),
            LocalDate.now().plusDays(5),
            SubscriptionStatus.ACTIVE);
    Subscription s2 =
        new Subscription(
            1L,
            2L,
            LocalDate.now().minusDays(1),
            LocalDate.now().plusDays(30),
            SubscriptionStatus.ACTIVE);
    when(repository.findByMemberId(1L)).thenReturn(List.of(s1, s2));
    ValidityResponse response = service().validity(1L);
    assertTrue(response.valid());
    assertEquals(s2.getId(), response.subscriptionId());
  }

  @Test
  void createSubscriptionWithValidMemberSucceeds() {
    MembershipPlan plan = new MembershipPlan("Plan A", 30, BigDecimal.TEN, "desc", true);
    when(planRepository.findById(2L)).thenReturn(Optional.of(plan));
    when(memberClient.getById(1L))
        .thenReturn(
            new MemberClientResponse(
                1L, "Rex", "rex@example.com", "123", LocalDate.now(), "ACTIVE", OffsetDateTime.now()));
    Subscription saved =
        new Subscription(
            1L,
            2L,
            LocalDate.now(),
            LocalDate.now().plusDays(30),
            SubscriptionStatus.ACTIVE);
    when(repository.save(any(Subscription.class))).thenReturn(saved);

    SubscriptionResponse response =
        service()
            .create(
                new SubscriptionRequest(
                    1L,
                    2L,
                    LocalDate.now(),
                    LocalDate.now().plusDays(30),
                    SubscriptionStatus.CANCELLED));
    assertNotNull(response);
    assertEquals(1L, response.memberId());
  }

  @Test
  void createActiveSubscriptionIsRejectedUntilPayment() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service()
                .create(
                    new SubscriptionRequest(
                        1L,
                        2L,
                        LocalDate.now(),
                        LocalDate.now().plusDays(30),
                        SubscriptionStatus.ACTIVE)));
    verify(repository, never()).save(any());
  }

  @Test
  void createSubscriptionWithMissingMemberThrowsNotFound() {
    MembershipPlan plan = new MembershipPlan("Plan A", 30, BigDecimal.TEN, "desc", true);
    when(planRepository.findById(2L)).thenReturn(Optional.of(plan));
    Request request =
        Request.create(Request.HttpMethod.GET, "/api/members/999", Collections.emptyMap(), null, new RequestTemplate());
    when(memberClient.getById(999L))
        .thenThrow(new FeignException.NotFound("Member not found", request, null, null));

    assertThrows(
        ResourceNotFoundException.class,
        () ->
            service()
                .create(
                    new SubscriptionRequest(
                        999L,
                        2L,
                        LocalDate.now(),
                        LocalDate.now().plusDays(30),
                        SubscriptionStatus.PENDING)));
  }

  @Test
  void createSubscriptionWithMemberServiceOutageThrowsUnavailable() {
    MembershipPlan plan = new MembershipPlan("Plan A", 30, BigDecimal.TEN, "desc", true);
    when(planRepository.findById(2L)).thenReturn(Optional.of(plan));
    Request request =
        Request.create(Request.HttpMethod.GET, "/api/members/1", Collections.emptyMap(), null, new RequestTemplate());
    when(memberClient.getById(1L))
        .thenThrow(new FeignException.ServiceUnavailable("Service Down", request, null, null));

    assertThrows(
        IllegalStateException.class,
        () ->
            service()
                .create(
                    new SubscriptionRequest(
                        1L,
                        2L,
                        LocalDate.now(),
                        LocalDate.now().plusDays(30),
                        SubscriptionStatus.PENDING)));
  }

  @Test
  void createSubscriptionWithSameDayDatesSucceeds() {
    MembershipPlan plan = new MembershipPlan("Plan A", 1, BigDecimal.TEN, "desc", true);
    when(planRepository.findById(2L)).thenReturn(Optional.of(plan));
    when(memberClient.getById(1L))
        .thenReturn(
            new MemberClientResponse(
                1L, "Rex", "rex@example.com", "123", LocalDate.now(), "ACTIVE", OffsetDateTime.now()));
    Subscription saved =
        new Subscription(1L, 2L, LocalDate.now(), LocalDate.now(), SubscriptionStatus.PENDING);
    when(repository.save(any(Subscription.class))).thenReturn(saved);

    SubscriptionResponse response =
        service()
            .create(
                new SubscriptionRequest(
                    1L, 2L, LocalDate.now(), LocalDate.now(), SubscriptionStatus.PENDING));
    assertNotNull(response);
  }

  @Test
  void createSubscriptionWithInvertedDatesThrowsBadRequest() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service()
                .create(
                    new SubscriptionRequest(
                        1L,
                        2L,
                        LocalDate.now().plusDays(10),
                        LocalDate.now(),
                        SubscriptionStatus.PENDING)));
  }

  @Test
  void unexpiredMembershipBlocksAnotherPlan() {
    MembershipPlan plan = new MembershipPlan("Other", 30, BigDecimal.TEN, "desc", true);
    when(planRepository.findById(8L)).thenReturn(Optional.of(plan));
    when(memberClient.getById(1L))
        .thenReturn(
            new MemberClientResponse(
                1L, "Rex", "rex@example.com", "123", LocalDate.now(), "ACTIVE", OffsetDateTime.now()));
    when(repository.findByMemberId(1L))
        .thenReturn(
            List.of(
                new Subscription(
                    1L, 2L, LocalDate.now().minusDays(1), LocalDate.now().plusDays(10), SubscriptionStatus.ACTIVE)));

    ConflictException ex =
        assertThrows(ConflictException.class, () -> service().preparePurchase(1L, 8L));
    assertTrue(ex.getMessage().contains(LocalDate.now().plusDays(10).toString()));
    verify(repository, never()).save(any());
  }

  @Test
  void expiredMembershipCanStartAPendingPurchase() {
    MembershipPlan plan = new MembershipPlan("Other", 15, BigDecimal.TEN, "desc", true);
    when(planRepository.findById(8L)).thenReturn(Optional.of(plan));
    when(memberClient.getById(1L))
        .thenReturn(
            new MemberClientResponse(
                1L, "Rex", "rex@example.com", "123", LocalDate.now(), "ACTIVE", OffsetDateTime.now()));
    when(repository.findByMemberId(1L))
        .thenReturn(
            List.of(
                new Subscription(
                    1L, 2L, LocalDate.now().minusDays(20), LocalDate.now().minusDays(1), SubscriptionStatus.ACTIVE)));
    when(repository.save(any(Subscription.class))).thenAnswer(invocation -> invocation.getArgument(0));

    SubscriptionResponse response = service().preparePurchase(1L, 8L);
    assertEquals(SubscriptionStatus.PENDING, response.status());
    verify(repository).lockMember(1L);
  }

  @Test
  void pendingPurchaseIsReused() {
    MembershipPlan plan = new MembershipPlan("Other", 15, BigDecimal.TEN, "desc", true);
    when(planRepository.findById(8L)).thenReturn(Optional.of(plan));
    when(memberClient.getById(1L))
        .thenReturn(
            new MemberClientResponse(
                1L, "Rex", "rex@example.com", "123", LocalDate.now(), "ACTIVE", OffsetDateTime.now()));
    Subscription pending =
        new Subscription(1L, 8L, LocalDate.now(), LocalDate.now().plusDays(15), SubscriptionStatus.PENDING);
    when(repository.findByMemberId(1L)).thenReturn(List.of(pending));

    SubscriptionResponse response = service().preparePurchase(1L, 8L);
    assertEquals(SubscriptionStatus.PENDING, response.status());
    verify(repository, never()).save(any());
  }
}
