package com.pulsefit.auth.bootstrap;

import com.pulsefit.auth.client.TrainerBootstrapClient;
import com.pulsefit.auth.client.TrainerBootstrapClient.BootstrapRequest;
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
  private final TrainerBootstrapClient trainers;

  @Value("${pulsefit.trainee.bootstrap.email:trainee@pulsefit.com}")
  private String email;

  @Value("${pulsefit.trainee.bootstrap.password:Trainee@PulseFit2026}")
  private String password;

  public TraineeBootstrapRunner(
      UserRepository users, PasswordEncoder passwordEncoder, TrainerBootstrapClient trainers) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.trainers = trainers;
  }

  @Override
  public void run(String... args) {
    User user = users.findByEmailIgnoreCase(email).orElse(null);
    if (user == null) {
      user =
          users.save(
              new User(
                  email,
                  passwordEncoder.encode(password),
                  "Trainee",
                  "Coach",
                  Role.TRAINEE,
                  null));
      System.out.println(">>> [Auth Service] Trainee account bootstrapped: " + email);
    } else if (user.getRole() != Role.TRAINEE || user.getMemberId() != null) {
      user.setRole(Role.TRAINEE);
      user.setMemberId(null);
      user.incrementTokenVersion();
      users.save(user);
      System.out.println(">>> [Auth Service] Trainee account updated to the trainee role: " + email);
    }
    linkTrainer(user.getEmail());
  }

  private void linkTrainer(String traineeEmail) {
    for (int attempt = 0; attempt < 8; attempt++) {
      try {
        trainers.bootstrap(new BootstrapRequest(traineeEmail, "Trainee Coach", 1L));
        System.out.println(">>> [Auth Service] Trainee trainer profile linked: " + traineeEmail);
        return;
      } catch (FeignException ex) {
        pause();
      }
    }
    System.out.println(">>> [Auth Service] Trainee trainer link skipped because the trainer service was unavailable.");
  }

  private static void pause() {
    try {
      Thread.sleep(2000);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }
}
