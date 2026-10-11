package com.pulsefit.auth.bootstrap;

import com.pulsefit.auth.client.CreateMemberRequest;
import com.pulsefit.auth.client.MemberDto;
import com.pulsefit.auth.client.MemberServiceClient;
import com.pulsefit.auth.model.Role;
import com.pulsefit.auth.model.User;
import com.pulsefit.auth.repository.UserRepository;
import feign.FeignException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class TraineeBootstrapRunner implements CommandLineRunner {
  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final MemberServiceClient members;

  @Value("${pulsefit.trainee.bootstrap.email:trainee@pulsefit.com}")
  private String email;

  @Value("${pulsefit.trainee.bootstrap.password:Trainee@PulseFit2026}")
  private String password;

  public TraineeBootstrapRunner(
      UserRepository users, PasswordEncoder passwordEncoder, MemberServiceClient members) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.members = members;
  }

  @Override
  public void run(String... args) {
    if (users.findByEmailIgnoreCase(email).isPresent()) {
      System.out.println(">>> [Auth Service] Trainee account already exists: " + email);
      return;
    }
    MemberDto member = findOrCreateMember();
    if (member == null || member.id() == null) {
      System.out.println(">>> [Auth Service] Trainee bootstrap skipped because the member profile was not created.");
      return;
    }
    users.save(
        new User(
            email,
            passwordEncoder.encode(password),
            "Trainee",
            "Member",
            Role.MEMBER,
            member.id()));
    System.out.println(">>> [Auth Service] Trainee account bootstrapped: " + email);
  }

  private MemberDto findOrCreateMember() {
    for (int attempt = 0; attempt < 5; attempt++) {
      try {
        MemberDto existing = members.findByEmail(email);
        if (existing != null && existing.id() != null) return existing;
      } catch (FeignException.NotFound ignored) {
        // Create the profile below.
      } catch (FeignException ex) {
        pause();
        continue;
      }
      try {
        return members.createMember(
            new CreateMemberRequest("Trainee Member", email, "555-0140", null, "ACTIVE"));
      } catch (FeignException ex) {
        pause();
      }
    }
    return null;
  }

  private static void pause() {
    try {
      Thread.sleep(2000);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }
}
