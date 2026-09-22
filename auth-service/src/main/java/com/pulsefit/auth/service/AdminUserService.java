package com.pulsefit.auth.service;

import com.pulsefit.auth.dto.*;
import com.pulsefit.auth.exception.*;
import com.pulsefit.auth.model.Role;
import com.pulsefit.auth.model.User;
import com.pulsefit.auth.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final AuditLogService auditLogService;

  public AdminUserService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      AuditLogService auditLogService) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.auditLogService = auditLogService;
  }

  @Transactional
  public UserSummary createStaff(
      StaffCreateRequest request, Long actorId, String actorEmail, String ipAddress) {
    if (userRepository.existsByEmail(request.email())) {
      auditLogService.log(
          actorId, actorEmail, ipAddress, "CREATE_STAFF", null, request.email(),
          "Attempted to create staff with existing email", "FAILURE", "Email already exists");
      throw new ConflictException("Email already in use: " + request.email());
    }

    User staff =
        new User(
            request.email(),
            passwordEncoder.encode(request.temporaryPassword()),
            request.firstName(),
            request.lastName(),
            Role.STAFF,
            null);

    User saved = userRepository.save(staff);
    auditLogService.log(
        actorId, actorEmail, ipAddress, "CREATE_STAFF", saved.getId(), saved.getEmail(),
        "Staff account provisioned", "SUCCESS", null);

    return toSummary(saved);
  }

  @Transactional
  public UserSummary createAdmin(
      AdminCreateRequest request, Long actorId, String actorEmail, String ipAddress) {
    if (userRepository.existsByEmail(request.email())) {
      auditLogService.log(
          actorId, actorEmail, ipAddress, "CREATE_ADMIN", null, request.email(),
          "Attempted to create admin with existing email", "FAILURE", "Email already exists");
      throw new ConflictException("Email already in use: " + request.email());
    }

    User admin =
        new User(
            request.email(),
            passwordEncoder.encode(request.temporaryPassword()),
            request.firstName(),
            request.lastName(),
            Role.ADMIN,
            null);

    User saved = userRepository.save(admin);
    auditLogService.log(
        actorId, actorEmail, ipAddress, "CREATE_ADMIN", saved.getId(), saved.getEmail(),
        "Admin account provisioned", "SUCCESS", null);

    return toSummary(saved);
  }

  @Transactional
  public UserSummary updateUserRole(
      Long targetUserId, RoleUpdateRequest request, Long actorId, String actorEmail, String ipAddress) {
    if (actorId != null && actorId.equals(targetUserId) && request.role() != Role.ADMIN) {
      throw new BadRequestException("Administrators cannot demote their own account. Another administrator must perform this action.");
    }

    User target =
        userRepository
            .findByIdWithLock(targetUserId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + targetUserId));

    Role oldRole = target.getRole();
    if (oldRole == Role.ADMIN && request.role() != Role.ADMIN && target.isEnabled()) {
      long activeAdmins = userRepository.countByRoleAndEnabled(Role.ADMIN, true);
      if (activeAdmins <= 1) {
        auditLogService.log(
            actorId, actorEmail, ipAddress, "UPDATE_ROLE", target.getId(), target.getEmail(),
            "Attempted to demote the last active administrator", "FAILURE", "Last-Admin Invariant");
        throw new ConflictException("Action rejected: PulseFit requires at least one active administrator at all times.");
      }
    }

    target.setRole(request.role());
    target.incrementTokenVersion(); // Invalidate existing active tokens
    User saved = userRepository.save(target);

    auditLogService.log(
        actorId, actorEmail, ipAddress, "UPDATE_ROLE", saved.getId(), saved.getEmail(),
        "Role changed from " + oldRole + " to " + request.role(), "SUCCESS", null);

    return toSummary(saved);
  }

  @Transactional
  public UserSummary updateUserStatus(
      Long targetUserId, StatusUpdateRequest request, Long actorId, String actorEmail, String ipAddress) {
    if (actorId != null && actorId.equals(targetUserId) && !request.enabled()) {
      throw new BadRequestException("Administrators cannot disable their own account.");
    }

    User target =
        userRepository
            .findByIdWithLock(targetUserId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + targetUserId));

    boolean oldStatus = target.isEnabled();
    if (target.getRole() == Role.ADMIN && oldStatus && !request.enabled()) {
      long activeAdmins = userRepository.countByRoleAndEnabled(Role.ADMIN, true);
      if (activeAdmins <= 1) {
        auditLogService.log(
            actorId, actorEmail, ipAddress, "UPDATE_STATUS", target.getId(), target.getEmail(),
            "Attempted to disable the last active administrator", "FAILURE", "Last-Admin Invariant");
        throw new ConflictException("Action rejected: PulseFit requires at least one active administrator at all times.");
      }
    }

    target.setEnabled(request.enabled());
    target.incrementTokenVersion(); // Invalidate existing active tokens
    User saved = userRepository.save(target);

    auditLogService.log(
        actorId, actorEmail, ipAddress, "UPDATE_STATUS", saved.getId(), saved.getEmail(),
        "Account status changed to enabled=" + request.enabled(), "SUCCESS", null);

    return toSummary(saved);
  }

  private UserSummary toSummary(User user) {
    return new UserSummary(
        user.getId(),
        user.getEmail(),
        user.getFirstName(),
        user.getLastName(),
        user.getRole(),
        user.getMemberId(),
        user.isEnabled(),
        user.getTokenVersion());
  }
}

