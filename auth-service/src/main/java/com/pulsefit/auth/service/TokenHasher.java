package com.pulsefit.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class TokenHasher {
  private static final SecureRandom RANDOM = new SecureRandom();

  private TokenHasher() {}

  public static String newToken() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public static String sha256(String token) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(digest.length * 2);
      for (byte value : digest) {
        hex.append(String.format("%02x", value));
      }
      return hex.toString();
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to hash token");
    }
  }
}
