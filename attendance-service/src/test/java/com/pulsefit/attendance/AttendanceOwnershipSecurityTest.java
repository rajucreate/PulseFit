package com.pulsefit.attendance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.pulsefit.attendance.client.SubscriptionClient;
import com.pulsefit.attendance.client.TraineeAssignmentClient;
import com.pulsefit.attendance.client.SubscriptionValidity;
import com.pulsefit.attendance.controller.AttendanceController;
import com.pulsefit.attendance.dto.AttendanceResponse;
import com.pulsefit.attendance.dto.CheckInRequest;
import com.pulsefit.attendance.model.AccessStatus;
import com.pulsefit.attendance.repository.AttendanceRepository;
import com.pulsefit.attendance.security.AuthenticatedUser;
import com.pulsefit.attendance.service.AttendanceService;
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

@ExtendWith(MockitoExtension.class)
class AttendanceOwnershipSecurityTest {

  @Mock private AttendanceRepository repository;
  @Mock private SubscriptionClient subscriptions;
  @Mock private TraineeAssignmentClient assignments;

  private AttendanceService attendanceService;
  private AttendanceController controller;

  @BeforeEach
  void setUp() {
    attendanceService = new AttendanceService(repository, subscriptions, null, null, null);
    controller = new AttendanceController(attendanceService, assignments);
  }

  @Test
  void memberCheckingInForOwnMemberId_Succeeds() {
    AuthenticatedUser user = new AuthenticatedUser(10L, "member@pulsefit.com", "MEMBER", 42L);
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            user, "token", Collections.singletonList(new SimpleGrantedAuthority("ROLE_MEMBER")));

    when(subscriptions.validity(42L)).thenReturn(new SubscriptionValidity(42L, true, 100L));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    AttendanceResponse response = controller.checkIn(new CheckInRequest(42L, 5L), auth);
    assertNotNull(response);
    assertEquals(AccessStatus.GRANTED, response.accessStatus());
  }

  @Test
  void memberCheckingInForAnotherMemberId_ThrowsAccessDenied() {
    AuthenticatedUser user = new AuthenticatedUser(10L, "member@pulsefit.com", "MEMBER", 42L);
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            user, "token", Collections.singletonList(new SimpleGrantedAuthority("ROLE_MEMBER")));

    assertThrows(AccessDeniedException.class, () -> controller.checkIn(new CheckInRequest(99L, 5L), auth));
    verify(repository, never()).save(any());
  }

  @Test
  void memberViewingOwnAttendance_Succeeds() {
    AuthenticatedUser user = new AuthenticatedUser(10L, "member@pulsefit.com", "MEMBER", 42L);
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            user, "token", Collections.singletonList(new SimpleGrantedAuthority("ROLE_MEMBER")));

    when(repository.findByMemberId(42L)).thenReturn(Collections.emptyList());

    List<AttendanceResponse> response = controller.byMember(42L, auth);
    assertNotNull(response);
  }

  @Test
  void memberViewingAnotherMemberAttendance_ThrowsAccessDenied() {
    AuthenticatedUser user = new AuthenticatedUser(10L, "member@pulsefit.com", "MEMBER", 42L);
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            user, "token", Collections.singletonList(new SimpleGrantedAuthority("ROLE_MEMBER")));

    assertThrows(AccessDeniedException.class, () -> controller.byMember(99L, auth));
    verify(repository, never()).findByMemberId(anyLong());
  }

  @Test
  void traineeCannotCheckInOrReadAnUnassignedMember() {
    AuthenticatedUser user = new AuthenticatedUser(11L, "trainee@pulsefit.com", "TRAINEE", null);
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            user, "token", Collections.singletonList(new SimpleGrantedAuthority("ROLE_TRAINEE")));
    when(assignments.assigned("trainee@pulsefit.com", 99L))
        .thenReturn(new TraineeAssignmentClient.AssignmentCheck(false));
    assertThrows(AccessDeniedException.class, () -> controller.checkIn(new CheckInRequest(99L, 1L), auth));
    assertThrows(AccessDeniedException.class, () -> controller.byMember(99L, auth));
    verify(repository, never()).findByMemberId(anyLong());
  }
}

