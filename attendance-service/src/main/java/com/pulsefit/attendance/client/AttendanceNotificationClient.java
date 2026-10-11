package com.pulsefit.attendance.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "NOTIFICATION-SERVICE", contextId = "attendanceNotificationClient")
public interface AttendanceNotificationClient {
  @PostMapping("/api/notifications/internal")
  void create(NotificationRequest request);

  record NotificationRequest(Long memberId, String type, String recipient, String subject, String message) {}
}
