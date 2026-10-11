package com.pulsefit.auth.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class ClubMailer {
  private final ObjectProvider<JavaMailSender> mailSender;
  private final String host;
  private final String from;

  public ClubMailer(
      ObjectProvider<JavaMailSender> mailSender,
      @Value("${spring.mail.host:}") String host,
      @Value("${pulsefit.mail.from:noreply@pulsefit.local}") String from) {
    this.mailSender = mailSender;
    this.host = host;
    this.from = from;
  }

  public boolean configured() {
    return host != null && !host.isBlank() && mailSender.getIfAvailable() != null;
  }

  public void send(String to, String subject, String text) {
    JavaMailSender sender = mailSender.getIfAvailable();
    if (!configured() || sender == null) {
      throw new IllegalStateException("SMTP is not configured");
    }
    SimpleMailMessage message = new SimpleMailMessage();
    message.setTo(to);
    message.setFrom(from);
    message.setSubject(subject);
    message.setText(text);
    sender.send(message);
  }
}
