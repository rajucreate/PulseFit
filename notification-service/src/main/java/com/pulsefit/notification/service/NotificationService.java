package com.pulsefit.notification.service;

import com.pulsefit.notification.dto.NotificationDtos.NotificationRequest;
import com.pulsefit.notification.dto.NotificationDtos.NotificationResponse;
import com.pulsefit.notification.exception.ResourceNotFoundException;
import com.pulsefit.notification.model.Notification;
import com.pulsefit.notification.model.NotificationStatus;
import com.pulsefit.notification.repository.NotificationRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
  private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
  private static final Set<String> TYPES =
      Set.of(
          "PAYMENT_SUCCESS",
          "PAYMENT_FAILED",
          "MEMBERSHIP_CREATED",
          "MEMBERSHIP_EXPIRY",
          "ATTENDANCE",
          "GENERAL");

  private final NotificationRepository repository;
  private final ObjectProvider<JavaMailSender> mailSender;
  private final String mode;
  private final String smtpHost;

  public NotificationService(
      NotificationRepository repository,
      ObjectProvider<JavaMailSender> mailSender,
      @Value("${pulsefit.notifications.mode:log}") String mode,
      @Value("${spring.mail.host:}") String smtpHost) {
    this.repository = repository;
    this.mailSender = mailSender;
    this.mode = mode;
    this.smtpHost = smtpHost;
  }

  @Transactional
  public NotificationResponse create(NotificationRequest request) {
    String type = request.type().trim().toUpperCase();
    if (!TYPES.contains(type)) {
      throw new IllegalArgumentException("Unsupported notification type: " + request.type());
    }
    Notification notification =
        new Notification(
            request.memberId(),
            type,
            request.recipient().trim(),
            request.subject().trim(),
            request.message().trim(),
            OffsetDateTime.now());
    deliver(notification);
    return toResponse(repository.save(notification));
  }

  @Transactional
  public NotificationResponse retry(Long id) {
    Notification notification = require(id);
    if (notification.getStatus() == NotificationStatus.SENT) {
      return toResponse(notification);
    }
    deliver(notification);
    return toResponse(repository.save(notification));
  }

  public NotificationResponse get(Long id) {
    return toResponse(require(id));
  }

  public List<NotificationResponse> byMember(Long memberId) {
    return repository.findByMemberIdOrderByCreatedAtDesc(memberId).stream().map(this::toResponse).toList();
  }

  public List<NotificationResponse> search(Long memberId, String type, NotificationStatus status) {
    String normalized = type == null || type.isBlank() ? null : type.trim().toUpperCase();
    return repository.search(memberId, normalized, status).stream().map(this::toResponse).toList();
  }

  private void deliver(Notification notification) {
    boolean smtpMode = "smtp".equalsIgnoreCase(mode);
    JavaMailSender sender = smtpMode ? mailSender.getIfAvailable() : null;
    boolean smtpReady = smtpMode && smtpHost != null && !smtpHost.isBlank() && sender != null;
    if (!smtpReady) {
      if ("smtp".equalsIgnoreCase(mode)) {
        notification.recordAttempt(NotificationStatus.FAILED, "SMTP is not configured, so the email was not sent.");
      } else {
        notification.recordAttempt(
            NotificationStatus.PENDING,
            "Development mode: notification stored and logged. Email was not sent.");
        log.info(
            "Notification for member {} to {}: {}",
            notification.getMemberId(),
            notification.getRecipient(),
            notification.getSubject());
      }
      return;
    }
    try {
      SimpleMailMessage mail = new SimpleMailMessage();
      mail.setTo(notification.getRecipient());
      mail.setSubject(notification.getSubject());
      mail.setText(notification.getMessage());
      sender.send(mail);
      notification.recordAttempt(NotificationStatus.SENT, "Delivered through configured SMTP.");
    } catch (Exception ex) {
      notification.recordAttempt(NotificationStatus.FAILED, "SMTP send failed: " + ex.getMessage());
      log.warn("SMTP send failed for notification {}: {}", notification.getId(), ex.getMessage());
    }
  }

  private Notification require(Long id) {
    return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + id));
  }

  private NotificationResponse toResponse(Notification notification) {
    return new NotificationResponse(
        notification.getId(),
        notification.getMemberId(),
        notification.getType(),
        notification.getRecipient(),
        notification.getSubject(),
        notification.getMessage(),
        notification.getStatus(),
        notification.getCreatedAt(),
        notification.getDeliveryNote(),
        notification.getAttempts());
  }
}
