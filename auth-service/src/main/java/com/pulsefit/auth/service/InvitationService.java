package com.pulsefit.auth.service;

import com.pulsefit.auth.client.CreateMemberRequest;
import com.pulsefit.auth.client.MemberDto;
import com.pulsefit.auth.client.MemberServiceClient;
import com.pulsefit.auth.dto.InvitationDtos.AcceptInvitationRequest;
import com.pulsefit.auth.dto.InvitationDtos.InvitationPreview;
import com.pulsefit.auth.dto.InvitationDtos.InvitationView;
import com.pulsefit.auth.dto.InvitationDtos.InviteRequest;
import com.pulsefit.auth.dto.InvitationDtos.MessageResponse;
import com.pulsefit.auth.exception.BadRequestException;
import com.pulsefit.auth.exception.ConflictException;
import com.pulsefit.auth.exception.ResourceNotFoundException;
import com.pulsefit.auth.model.AccountToken;
import com.pulsefit.auth.model.AccountToken.Purpose;
import com.pulsefit.auth.model.Role;
import com.pulsefit.auth.model.User;
import com.pulsefit.auth.repository.AccountTokenRepository;
import com.pulsefit.auth.repository.UserRepository;
import feign.FeignException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvitationService {
  private final AccountTokenRepository tokens;
  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final MemberServiceClient members;
  private final ClubMailer mailer;
  private final String publicUrl;
  private final long hours;

  public InvitationService(
      AccountTokenRepository tokens,
      UserRepository users,
      PasswordEncoder passwordEncoder,
      MemberServiceClient members,
      ClubMailer mailer,
      @Value("${pulsefit.app.public-url:http://localhost:5173}") String publicUrl,
      @Value("${pulsefit.invitation.hours:24}") long hours) {
    this.tokens = tokens;
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.members = members;
    this.mailer = mailer;
    this.publicUrl = publicUrl.replaceAll("/$", "");
    this.hours = hours;
  }

  @Transactional
  public InvitationView create(InviteRequest request) {
    String email = normalize(request.email());
    String name = request.fullName().trim();
    if (users.findByEmailIgnoreCase(email).isPresent()) {
      throw new ConflictException("An account already uses this email.");
    }
    if (tokens
        .findFirstByEmailAndPurposeAndConsumedAtIsNullAndExpiresAtAfter(
            email, Purpose.INVITE, LocalDateTime.now())
        .isPresent()) {
      throw new ConflictException(
          "An invitation is already pending for this email. Resend it from the invitation list.");
    }
    String raw = TokenHasher.newToken();
    AccountToken saved =
        tokens.save(
            new AccountToken(
                email, name, TokenHasher.sha256(raw), Purpose.INVITE, LocalDateTime.now().plusHours(hours)));
    return deliver(saved, raw, true);
  }

  public List<InvitationView> list() {
    return tokens.findByPurposeOrderByCreatedAtDesc(Purpose.INVITE).stream()
        .map(token -> view(token, "SAVED", "Invitation record.", null))
        .toList();
  }

  @Transactional
  public InvitationView resend(Long id) {
    AccountToken token =
        tokens.findById(id).orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));
    if (token.getPurpose() != Purpose.INVITE) {
      throw new ResourceNotFoundException("Invitation not found");
    }
    if (users.findByEmailIgnoreCase(token.getEmail()).isPresent()) {
      throw new ConflictException("An account already uses this email.");
    }
    String raw = TokenHasher.newToken();
    token.rotate(TokenHasher.sha256(raw), LocalDateTime.now().plusHours(hours));
    return deliver(tokens.save(token), raw, true);
  }

  public InvitationPreview preview(String rawToken) {
    AccountToken token = tokens.findByTokenHash(TokenHasher.sha256(blank(rawToken))).orElse(null);
    if (token == null || token.getPurpose() != Purpose.INVITE) {
      return new InvitationPreview(false, "INVALID", null, null);
    }
    if (token.getConsumedAt() != null) {
      return new InvitationPreview(false, "USED", null, null);
    }
    if (!token.getExpiresAt().isAfter(LocalDateTime.now())) {
      return new InvitationPreview(false, "EXPIRED", null, null);
    }
    return new InvitationPreview(true, "VALID", token.getEmail(), token.getFullName());
  }

  @Transactional
  public MessageResponse accept(AcceptInvitationRequest request) {
    if (request.password() == null || request.password().length() < 8) {
      throw new BadRequestException("Password must be at least 8 characters long");
    }
    AccountToken token =
        tokens.findByTokenHash(TokenHasher.sha256(request.token())).orElse(null);
    if (token == null || token.getPurpose() != Purpose.INVITE) {
      throw new BadRequestException("This invitation link is invalid.");
    }
    LocalDateTime now = LocalDateTime.now();
    if (token.getConsumedAt() != null) {
      throw new BadRequestException("This invitation link has already been used.");
    }
    if (!token.getExpiresAt().isAfter(now)) {
      throw new BadRequestException("This invitation link has expired.");
    }
    if (tokens.consumeIfOpen(token.getId(), now) != 1) {
      throw new BadRequestException("This invitation link has already been used.");
    }
    if (users.findByEmailIgnoreCase(token.getEmail()).isPresent()) {
      throw new ConflictException("An account already uses this email.");
    }
    String[] names = splitName(token.getFullName());
    MemberDto member = findOrCreateMember(token.getEmail(), token.getFullName());
    users.save(
        new User(
            token.getEmail(),
            passwordEncoder.encode(request.password()),
            names[0],
            names[1],
            Role.MEMBER,
            member.id()));
    return new MessageResponse("Your password is set. Sign in with your email.");
  }

  private MemberDto findOrCreateMember(String email, String fullName) {
    try {
      MemberDto existing = members.findByEmail(email);
      if (existing != null) return existing;
    } catch (FeignException.NotFound ignored) {
      // A new member profile is created below.
    } catch (FeignException ex) {
      throw new BadRequestException("Member profiles are unavailable. Try again shortly.");
    }
    try {
      return members.createMember(
          new CreateMemberRequest(fullName, email, "Not provided", null, "ACTIVE"));
    } catch (FeignException ex) {
      throw new BadRequestException("The member profile could not be created.");
    }
  }

  private InvitationView deliver(AccountToken token, String rawToken, boolean includeDevLink) {
    String url = publicUrl + "/invite/" + rawToken;
    if (!mailer.configured()) {
      return view(
          token,
          "NOT_CONFIGURED",
          "Invitation saved. Email was not sent because SMTP is not configured.",
          includeDevLink ? url : null);
    }
    try {
      mailer.send(
          token.getEmail(),
          "Your PulseFit invitation",
          "Set your PulseFit password with this single-use link. It expires in "
              + hours
              + " hours.\n\n"
              + url
              + "\n\nIf you did not expect this invitation, ignore this email.");
      return view(token, "SENT", "Invitation email sent.", null);
    } catch (RuntimeException ex) {
      return view(token, "FAILED", "Invitation was saved, but the email could not be sent.", null);
    }
  }

  private InvitationView view(AccountToken token, String delivery, String message, String url) {
    return new InvitationView(
        token.getId(),
        token.getEmail(),
        token.getFullName(),
        statusOf(token),
        delivery,
        message,
        url);
  }

  private String statusOf(AccountToken token) {
    if (token.getConsumedAt() != null) return "CONSUMED";
    if (!token.getExpiresAt().isAfter(LocalDateTime.now())) return "EXPIRED";
    return "PENDING";
  }

  private static String normalize(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private static String blank(String value) {
    return value == null ? "" : value;
  }

  private static String[] splitName(String fullName) {
    String trimmed = fullName == null ? "" : fullName.trim();
    int space = trimmed.indexOf(' ');
    if (space < 0) return new String[] {trimmed.isBlank() ? "Member" : trimmed, "Member"};
    return new String[] {trimmed.substring(0, space), trimmed.substring(space + 1).trim()};
  }
}
