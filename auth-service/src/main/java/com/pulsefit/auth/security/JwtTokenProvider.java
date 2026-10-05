package com.pulsefit.auth.security;

import com.pulsefit.auth.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

  @Value("${pulsefit.jwt.private-key-path:classpath:jwt-private.pem}")
  private String privateKeyPath;

  @Value("${pulsefit.jwt.public-key-path:classpath:jwt-public.pem}")
  private String publicKeyPath;

  @Value("${pulsefit.jwt.issuer:pulsefit-auth-service}")
  private String issuer;

  @Value("${pulsefit.jwt.audience:pulsefit-api}")
  private String audience;

  @Value("${pulsefit.jwt.expiration-minutes:15}")
  private long expirationMinutes;

  private final ResourceLoader resourceLoader;
  private PrivateKey privateKey;
  private PublicKey publicKey;

  public JwtTokenProvider(ResourceLoader resourceLoader) {
    this.resourceLoader = resourceLoader;
  }

  @PostConstruct
  public void init() {
    try {
      this.privateKey = loadPrivateKey(privateKeyPath);
      this.publicKey = loadPublicKey(publicKeyPath);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to load RSA keys for JWT provider", e);
    }
  }

  public String generateToken(User user) {
    Date now = new Date();
    Date expiry = new Date(now.getTime() + expirationMinutes * 60 * 1000);

    var builder = Jwts.builder()
        .issuer(issuer)
        .audience().add(audience).and()
        .subject(user.getId().toString())
        .claim("email", user.getEmail())
        .claim("role", user.getRole().name())
        .claim("ver", user.getTokenVersion())
        .issuedAt(now)
        .expiration(expiry)
        .signWith(privateKey, Jwts.SIG.RS256);

    if (user.getMemberId() != null) {
      builder.claim("memberId", user.getMemberId());
    }

    return builder.compact();
  }

  public Claims validateAndExtractClaims(String token) {
    return Jwts.parser()
        .verifyWith(publicKey)
        .requireIssuer(issuer)
        .requireAudience(audience)
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  public long getExpirationSeconds() {
    return expirationMinutes * 60;
  }

  public PublicKey getPublicKey() {
    return publicKey;
  }

  private PrivateKey loadPrivateKey(String location) throws Exception {
    Resource resource = resolvePrivateKey(location);
    try (InputStream is = resource.getInputStream()) {
      String keyStr = new String(is.readAllBytes(), StandardCharsets.UTF_8);
      keyStr = keyStr.replace("-----BEGIN PRIVATE KEY-----", "")
          .replace("-----END PRIVATE KEY-----", "")
          .replaceAll("\\s+", "");
      byte[] decoded = Base64.getDecoder().decode(keyStr);
      PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
      return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
    }
  }

  /**
   * STS 4 runs a Spring Boot app with the service project as the working directory
   * ({@code auth-service}). A workspace-root launch looks one folder deeper.
   */
  private Resource resolvePrivateKey(String configuredLocation) {
    String[] candidates = {
      configuredLocation,
      "file:./config/secrets/jwt-private.pem",
      "file:./auth-service/config/secrets/jwt-private.pem"
    };
    Resource fallback = null;
    for (String candidate : candidates) {
      if (candidate == null || candidate.isBlank()) {
        continue;
      }
      Resource resource = resourceLoader.getResource(candidate);
      if (resource.exists()) {
        return resource;
      }
      fallback = resource;
    }
    return fallback;
  }

  private PublicKey loadPublicKey(String location) throws Exception {
    Resource resource = resourceLoader.getResource(location);
    try (InputStream is = resource.getInputStream()) {
      String keyStr = new String(is.readAllBytes(), StandardCharsets.UTF_8);
      keyStr = keyStr.replace("-----BEGIN PUBLIC KEY-----", "")
          .replace("-----END PUBLIC KEY-----", "")
          .replaceAll("\\s+", "");
      byte[] decoded = Base64.getDecoder().decode(keyStr);
      X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
      return KeyFactory.getInstance("RSA").generatePublic(keySpec);
    }
  }
}

