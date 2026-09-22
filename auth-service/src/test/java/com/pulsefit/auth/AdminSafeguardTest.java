package com.pulsefit.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.pulsefit.auth.dto.*;
import com.pulsefit.auth.exception.*;
import com.pulsefit.auth.model.Role;
import com.pulsefit.auth.model.User;
import com.pulsefit.auth.repository.UserRepository;
import com.pulsefit.auth.service.AdminUserService;
import com.pulsefit.auth.service.AuditLogService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminSafeguardTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private com.pulsefit.auth.repository.AuditLogRepository auditLogRepository;

  private AuditLogService auditLogService;
  private AdminUserService adminUserService;

  @BeforeEach
  void setUp() {
    auditLogService = new AuditLogService(auditLogRepository);
    adminUserService = new AdminUserService(userRepository, passwordEncoder, auditLogService);
  }

  @Test
  void selfDemotion_IsRejected() {
    RoleUpdateRequest req = new RoleUpdateRequest(Role.STAFF);
    assertThrows(
        BadRequestException.class,
        () -> adminUserService.updateUserRole(1L, req, 1L, "admin@pulsefit.com", "127.0.0.1"));
  }

  @Test
  void selfDeactivation_IsRejected() {
    StatusUpdateRequest req = new StatusUpdateRequest(false);
    assertThrows(
        BadRequestException.class,
        () -> adminUserService.updateUserStatus(1L, req, 1L, "admin@pulsefit.com", "127.0.0.1"));
  }

  @Test
  void demotingLastAdmin_ThrowsConflictException() {
    User admin2 = new User("admin2@pulsefit.com", "hash", "Admin", "Two", Role.ADMIN, null);
    admin2.setId(2L);
    admin2.setEnabled(true);

    when(userRepository.findByIdWithLock(2L)).thenReturn(Optional.of(admin2));
    when(userRepository.countByRoleAndEnabled(Role.ADMIN, true)).thenReturn(1L);

    RoleUpdateRequest req = new RoleUpdateRequest(Role.STAFF);
    assertThrows(
        ConflictException.class,
        () -> adminUserService.updateUserRole(2L, req, 1L, "admin1@pulsefit.com", "127.0.0.1"));
  }

  @Test
  void disablingLastAdmin_ThrowsConflictException() {
    User admin2 = new User("admin2@pulsefit.com", "hash", "Admin", "Two", Role.ADMIN, null);
    admin2.setId(2L);
    admin2.setEnabled(true);

    when(userRepository.findByIdWithLock(2L)).thenReturn(Optional.of(admin2));
    when(userRepository.countByRoleAndEnabled(Role.ADMIN, true)).thenReturn(1L);

    StatusUpdateRequest req = new StatusUpdateRequest(false);
    assertThrows(
        ConflictException.class,
        () -> adminUserService.updateUserStatus(2L, req, 1L, "admin1@pulsefit.com", "127.0.0.1"));
  }

  @Test
  void demotingAdminWhenOtherAdminsExist_Succeeds() {
    User admin2 = new User("admin2@pulsefit.com", "hash", "Admin", "Two", Role.ADMIN, null);
    admin2.setId(2L);
    admin2.setEnabled(true);

    when(userRepository.findByIdWithLock(2L)).thenReturn(Optional.of(admin2));
    when(userRepository.countByRoleAndEnabled(Role.ADMIN, true)).thenReturn(2L);
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    RoleUpdateRequest req = new RoleUpdateRequest(Role.STAFF);
    UserSummary result = adminUserService.updateUserRole(2L, req, 1L, "admin1@pulsefit.com", "127.0.0.1");

    assertNotNull(result);
    assertEquals(Role.STAFF, result.role());
  }
}

