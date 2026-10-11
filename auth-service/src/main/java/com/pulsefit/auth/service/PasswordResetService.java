package com.pulsefit.auth.service;

import com.pulsefit.auth.dto.InvitationDtos.ForgotPasswordRequest;
import com.pulsefit.auth.dto.InvitationDtos.MessageResponse;
import com.pulsefit.auth.dto.InvitationDtos.ResetPasswordRequest;
import com.pulsefit.auth.exception.BadRequestException;
import com.pulsefit.auth.model.AccountToken;
import com.pulsefit.auth.model.AccountToken.Purpose;
import com.pulsefit.auth.model.User;
import com.pulsefit.auth.repository.AccountTokenRepository;
import com.pulsefit.auth.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {
  public static final String GENERIC =
      "If an account exists for that email, a password reset link has been sent.";

  private final AccountTokenRepository tokens;
  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final ClubMailer mailer;
  private final String publicUrl;
  private final long minutes;

  public PasswordResetService(
      AccountTokenRepository tokens,
      UserRepository users,
      PasswordEncoder passwordEncoder,
      ClubMailer mailer,
      @Value("${pulsefit.app.public-url:http://localhost:5173}") String publicUrl,
      @Value("${pulsefit.reset.minutes:30}") long minutes) {
    this.tokens = tokens;
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.mailer = mailer;
    this.publicUrl = publicUrl.replaceAll("/$", "");
    this.minutes = minutes;
  }

  @Transactional
  public MessageResponse request(ForgotPasswordRequest request) {
    String email = request.email().trim().toLowerCase(Locale.ROOT);
    User user = users.findByEmailIgnoreCase(email).orElse(null);
    if (user == null || !user.isEnabled()) {
      return new MessageResponse(GENERIC);
    }
    long recent =
        tokens.countByEmailAndPurposeAndCreatedAtAfter(
            user.getEmail(), Purpose.RESET, LocalDateTime.now().minusHours(1));
    if (recent >= 5) {
      return new MessageResponse(GENERIC);
    }
    String raw = TokenHasher.newToken();
    tokens.save(
        new AccountToken(
            user.getEmail(),
            user.getFirstName() + " " + user.getLastName(),
            TokenHasher.sha256(raw),
            Purpose.RESET,
            LocalDateTime.now().plusMinutes(minutes)));
    if (mailer.configured()) {
      try {
        mailer.send(
            user.getEmail(),
            "Reset your PulseFit password",
            "Use this single-use link to choose a new password. It expires in "
                + minutes
                + " minutes.\n\n"
                + publicUrl
                + "/reset/"
                + raw
                + "\n\nIf you did not ask for this, ignore this email.");
      } catch (RuntimeException ignored) {
        // The caller still receives the generic response.
      }
    }
    return new MessageResponse(GENERIC);
  }

  @Transactional
  public MessageResponse reset(ResetPasswordRequest request) {
    if (request.password() == null || request.password().length() < 8) {
      throw new BadRequestException("Password must be at least 8 characters long");
    }
    AccountToken token = tokens.findByTokenHash(TokenHasher.sha256(request.token())).orElse(null);
    if (token == null || token.getPurpose() != Purpose.RESET) {
      throw new BadRequestException("This reset link is invalid.");
    }
    LocalDateTime now = LocalDateTime.now();
    if (token.getConsumedAt() != null) {
      throw new BadRequestException("This reset link has already been used.");
    }
    if (!token.getExpiresAt().isAfter(now)) {
      throw new BadRequestException("This reset link has expired.");
    }
    if (tokens.consumeIfOpen(token.getId(), now) != 1) {
      throw new BadRequestException("This reset link has already been used.");
    }
    User user =
        users
            .findByEmailIgnoreCase(token.getEmail())
            .orElseThrow(() -> new BadRequestException("This reset link is invalid."));
    user.setPasswordHash(passwordEncoder.encode(request.password()));
    user.incrementTokenVersion();
    users.save(user);
    return new MessageResponse("Your password has been updated. Sign in with the new password.");
  }
}
