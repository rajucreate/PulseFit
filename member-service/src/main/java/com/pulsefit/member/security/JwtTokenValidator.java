package com.pulsefit.member.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenValidator {

  @Value("${pulsefit.jwt.public-key-path:classpath:jwt-public.pem}")
  private String publicKeyPath;

  @Value("${pulsefit.jwt.issuer:pulsefit-auth-service}")
  private String issuer;

  @Value("${pulsefit.jwt.audience:pulsefit-api}")
  private String audience;

  private final ResourceLoader resourceLoader;
  private PublicKey publicKey;

  public JwtTokenValidator(ResourceLoader resourceLoader) {
    this.resourceLoader = resourceLoader;
  }

  @PostConstruct
  public void init() {
    try {
      this.publicKey = loadPublicKey(publicKeyPath);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to load RSA public key in member-service", e);
    }
  }

  public AuthenticatedUser validateAndExtractUser(String token) {
    Claims claims =
        Jwts.parser()
            .verifyWith(publicKey)
            .requireIssuer(issuer)
            .requireAudience(audience)
            .build()
            .parseSignedClaims(token)
            .getPayload();

    Long userId = Long.valueOf(claims.getSubject());
    String email = claims.get("email", String.class);
    String role = claims.get("role", String.class);
    Object rawMemberId = claims.get("memberId");
    Long memberId = rawMemberId instanceof Number num ? num.longValue() : null;
    if (rawMemberId != null && memberId == null) {
      throw new IllegalArgumentException("Invalid memberId claim in token: " + rawMemberId);
    }

    return new AuthenticatedUser(userId, email, role, memberId);
  }

  private PublicKey loadPublicKey(String location) throws Exception {
    Resource resource = resourceLoader.getResource(location);
    try (InputStream is = resource.getInputStream()) {
      String keyStr = new String(is.readAllBytes(), StandardCharsets.UTF_8);
      keyStr =
          keyStr
              .replace("-----BEGIN PUBLIC KEY-----", "")
              .replace("-----END PUBLIC KEY-----", "")
              .replaceAll("\\s+", "");
      byte[] decoded = Base64.getDecoder().decode(keyStr);
      X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
      return KeyFactory.getInstance("RSA").generatePublic(keySpec);
    }
  }
}

