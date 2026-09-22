package com.pulsefit.attendance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.pulsefit.attendance.client.SubscriptionClient;
import com.pulsefit.attendance.client.SubscriptionValidity;
import com.pulsefit.attendance.dto.AttendanceResponse;
import com.pulsefit.attendance.dto.CheckInRequest;
import com.pulsefit.attendance.model.AccessStatus;
import com.pulsefit.attendance.repository.AttendanceRepository;
import com.pulsefit.attendance.service.AttendanceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {
  @Mock AttendanceRepository repository;
  @Mock SubscriptionClient subscriptions;

  @Test
  void validMemberGetsGrantedAccess() {
    when(subscriptions.validity(1L)).thenReturn(new SubscriptionValidity(1L, true, 42L));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
    AttendanceResponse response =
        new AttendanceService(repository, subscriptions).checkIn(new CheckInRequest(1L, 10L));
    assertEquals(AccessStatus.GRANTED, response.accessStatus());
    assertEquals(42L, response.subscriptionId());
    assertNull(response.denialReason());
  }

  @Test
  void invalidMemberGetsDeniedAccess() {
    when(subscriptions.validity(1L)).thenReturn(new SubscriptionValidity(1L, false, null));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
    AttendanceResponse response =
        new AttendanceService(repository, subscriptions).checkIn(new CheckInRequest(1L, 10L));
    assertEquals(AccessStatus.DENIED, response.accessStatus());
    assertNull(response.subscriptionId());
    assertEquals("No valid active subscription", response.denialReason());
  }
}
