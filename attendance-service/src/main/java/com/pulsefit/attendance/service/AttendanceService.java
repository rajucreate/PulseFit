package com.pulsefit.attendance.service;

import com.pulsefit.attendance.client.AttendanceNotificationClient;
import com.pulsefit.attendance.client.FacilityClient;
import com.pulsefit.attendance.client.MemberLookupClient;
import com.pulsefit.attendance.client.SubscriptionClient;
import com.pulsefit.attendance.client.SubscriptionValidity;
import com.pulsefit.attendance.dto.*;
import com.pulsefit.attendance.exception.ResourceNotFoundException;
import com.pulsefit.attendance.model.*;
import com.pulsefit.attendance.repository.AttendanceRepository;
import feign.FeignException;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AttendanceService {
  private static final Logger log = LoggerFactory.getLogger(AttendanceService.class);
  private final AttendanceRepository repository;
  private final SubscriptionClient subscriptions;
  private final FacilityClient facilities;
  private final MemberLookupClient members;
  private final AttendanceNotificationClient notifications;

  public AttendanceService(
      AttendanceRepository repository,
      SubscriptionClient subscriptions,
      FacilityClient facilities,
      MemberLookupClient members,
      AttendanceNotificationClient notifications) {
    this.repository = repository;
    this.subscriptions = subscriptions;
    this.facilities = facilities;
    this.members = members;
    this.notifications = notifications;
  }

  public AttendanceResponse checkIn(CheckInRequest request) {
    String facilityDenial = facilityDenial(request.facilityId());
    if (facilityDenial != null) {
      return response(save(request, null, AccessStatus.DENIED, facilityDenial));
    }
    SubscriptionValidity validity;
    try {
      validity = subscriptions.validity(request.memberId());
    } catch (FeignException e) {
      throw new IllegalStateException("Subscription service is unavailable");
    }
    boolean valid = validity != null && validity.valid();
    Long subscriptionId = valid ? validity.subscriptionId() : null;
    Attendance saved =
        save(
            request,
            subscriptionId,
            valid ? AccessStatus.GRANTED : AccessStatus.DENIED,
            valid ? null : "No valid active subscription");
    if (valid) {
      notifyGranted(saved);
    }
    return response(saved);
  }

  private String facilityDenial(Long facilityId) {
    if (facilities == null) return null;
    try {
      FacilityClient.FacilityView facility = facilities.get(facilityId);
      if (facility == null || facility.status() == null || !"ACTIVE".equalsIgnoreCase(facility.status())) {
        return "Facility is not active";
      }
      return null;
    } catch (FeignException.NotFound ex) {
      return "Unknown facility";
    } catch (FeignException ex) {
      log.warn("Facility service unavailable during check-in for facility {}", facilityId);
      return null;
    }
  }

  private Attendance save(CheckInRequest request, Long subscriptionId, AccessStatus status, String reason) {
    return repository.save(
        new Attendance(
            request.memberId(),
            subscriptionId,
            request.facilityId(),
            OffsetDateTime.now(),
            status,
            reason));
  }

  private void notifyGranted(Attendance attendance) {
    if (notifications == null || members == null) return;
    try {
      MemberLookupClient.MemberView member = members.get(attendance.getMemberId());
      if (member.email() == null || member.email().isBlank()) return;
      notifications.create(
          new AttendanceNotificationClient.NotificationRequest(
              attendance.getMemberId(),
              "ATTENDANCE",
              member.email(),
              "PulseFit check-in",
              "Access granted at facility " + attendance.getFacilityId() + "."));
    } catch (Exception ex) {
      log.warn("Check-in saved without a notification: {}", ex.getMessage());
    }
  }

  public List<AttendanceResponse> all() {
    return repository.findAll().stream().map(this::response).toList();
  }

  public AttendanceResponse get(Long id) {
    return response(
        repository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Attendance not found: " + id)));
  }

  public List<AttendanceResponse> byMember(Long id) {
    return repository.findByMemberId(id).stream().map(this::response).toList();
  }

  private AttendanceResponse response(Attendance a) {
    return new AttendanceResponse(
        a.getId(),
        a.getMemberId(),
        a.getSubscriptionId(),
        a.getFacilityId(),
        a.getCheckInTime(),
        a.getAccessStatus(),
        a.getDenialReason());
  }
}
