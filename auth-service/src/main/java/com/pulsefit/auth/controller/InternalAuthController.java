package com.pulsefit.auth.controller;

import com.pulsefit.auth.exception.ResourceNotFoundException;
import com.pulsefit.auth.model.User;
import com.pulsefit.auth.repository.UserRepository;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/internal")
public class InternalAuthController {

  private final UserRepository userRepository;

  public InternalAuthController(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @GetMapping("/users/{userId}/token-version")
  public Map<String, Object> getTokenVersion(@PathVariable Long userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

    return Map.of(
        "userId", user.getId(),
        "enabled", user.isEnabled(),
        "tokenVersion", user.getTokenVersion(),
        "role", user.getRole().name());
  }
}

