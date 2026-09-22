package com.pulsefit.auth.bootstrap;

import com.pulsefit.auth.model.Role;
import com.pulsefit.auth.model.User;
import com.pulsefit.auth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrapRunner implements CommandLineRunner {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Value("${pulsefit.admin.bootstrap.email:admin@pulsefit.com}")
  private String adminEmail;

  @Value("${pulsefit.admin.bootstrap.password:Admin@PulseFit2026}")
  private String adminPassword;

  @Value("${pulsefit.admin.bootstrap.first-name:System}")
  private String adminFirstName;

  @Value("${pulsefit.admin.bootstrap.last-name:Administrator}")
  private String adminLastName;

  public AdminBootstrapRunner(UserRepository userRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  @Transactional
  public void run(String... args) {
    long adminCount = userRepository.countByRoleAndEnabled(Role.ADMIN, true);
    if (adminCount == 0) {
      if (!userRepository.existsByEmail(adminEmail)) {
        User rootAdmin =
            new User(
                adminEmail,
                passwordEncoder.encode(adminPassword),
                adminFirstName,
                adminLastName,
                Role.ADMIN,
                null);
        userRepository.save(rootAdmin);
        System.out.println(">>> [Auth Service] Initial Administrator bootstrapped: " + adminEmail);
      }
    } else {
      System.out.println(">>> [Auth Service] Active administrators already exist (" + adminCount + "). Bootstrap skipped.");
    }
  }
}

