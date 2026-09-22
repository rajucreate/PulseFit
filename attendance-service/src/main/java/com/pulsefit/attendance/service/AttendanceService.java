package com.pulsefit.attendance.service;

import com.pulsefit.attendance.client.SubscriptionClient;
import com.pulsefit.attendance.client.SubscriptionValidity;
import com.pulsefit.attendance.dto.*;
import com.pulsefit.attendance.exception.ResourceNotFoundException;
import com.pulsefit.attendance.model.*;
import com.pulsefit.attendance.repository.AttendanceRepository;
import feign.FeignException;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AttendanceService {
  private final AttendanceRepository repository;
  private final SubscriptionClient subscriptions;

  public AttendanceService(AttendanceRepository repository, SubscriptionClient subscriptions) {
    this.repository = repository;
    this.subscriptions = subscriptions;
  }

  public AttendanceResponse checkIn(CheckInRequest request) {
    SubscriptionValidity validity;
    try {
      validity = subscriptions.validity(request.memberId());
    } catch (FeignException e) {
      throw new IllegalStateException("Subscription service is unavailable");
    }
    boolean valid = validity != null && validity.valid();
    Long subscriptionId = valid ? validity.subscriptionId() : null;
    Attendance a =
        new Attendance(
            request.memberId(),
            subscriptionId,
            request.facilityId(),
            OffsetDateTime.now(),
            valid ? AccessStatus.GRANTED : AccessStatus.DENIED,
            valid ? null : "No valid active subscription");
    return response(repository.save(a));
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
