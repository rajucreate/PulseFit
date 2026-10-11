package com.pulsefit.notification;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.pulsefit.notification.dto.NotificationDtos.NotificationRequest;
import com.pulsefit.notification.dto.NotificationDtos.NotificationResponse;
import com.pulsefit.notification.model.Notification;
import com.pulsefit.notification.model.NotificationStatus;
import com.pulsefit.notification.repository.NotificationRepository;
import com.pulsefit.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
  @Mock private NotificationRepository repository;
  @Mock private ObjectProvider<JavaMailSender> mailSender;

  @BeforeEach
  void setUp() {
    when(repository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void developmentModeStoresPendingAndDoesNotClaimDelivery() {
    NotificationService service = new NotificationService(repository, mailSender, "log", "");
    NotificationResponse response =
        service.create(new NotificationRequest(1L, "PAYMENT_SUCCESS", "a@b.com", "Paid", "Simulated payment"));
    assertEquals(NotificationStatus.PENDING, response.status());
    assertTrue(response.deliveryNote().contains("not sent"));
    verify(mailSender, never()).getIfAvailable();
  }

  @Test
  void smtpModeWithoutHostIsFailedRatherThanSent() {
    NotificationService service = new NotificationService(repository, mailSender, "smtp", "");
    NotificationResponse response =
        service.create(new NotificationRequest(1L, "GENERAL", "a@b.com", "Hello", "Body"));
    assertEquals(NotificationStatus.FAILED, response.status());
    assertFalse(response.deliveryNote().toLowerCase().contains("delivered"));
  }
}
