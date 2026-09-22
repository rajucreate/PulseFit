package com.pulsefit.attendance;

import static org.junit.jupiter.api.Assertions.*;

import com.pulsefit.attendance.security.AuthenticatedUser;
import com.pulsefit.attendance.security.JwtTokenValidator;
import io.jsonwebtoken.Jwts;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;

class JwtTokenValidatorTest {

  private JwtTokenValidator validator;
  private PrivateKey privateKey;

  @BeforeEach
  void setUp() throws Exception {
    validator = new JwtTokenValidator(new DefaultResourceLoader());
    ReflectionTestUtils.setField(validator, "publicKeyPath", "classpath:jwt-public.pem");
    ReflectionTestUtils.setField(validator, "issuer", "pulsefit-auth-service");
    ReflectionTestUtils.setField(validator, "audience", "pulsefit-api");
    validator.init();

    privateKey = loadPrivateKey("classpath:jwt-private-test.pem");
  }

  @Test
  void validateAndExtractUser_WithIntegerMemberId_Succeeds() {
    String token =
        Jwts.builder()
            .issuer("pulsefit-auth-service")
            .audience().add("pulsefit-api").and()
            .subject("10")
            .claim("email", "member@pulsefit.com")
            .claim("role", "MEMBER")
            .claim("memberId", Integer.valueOf(5))
            .claim("ver", 1L)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 60000))
            .signWith(privateKey, Jwts.SIG.RS256)
            .compact();

    AuthenticatedUser user = validator.validateAndExtractUser(token);
    assertNotNull(user);
    assertEquals(10L, user.userId());
    assertEquals("member@pulsefit.com", user.email());
    assertEquals("MEMBER", user.role());
    assertEquals(5L, user.memberId());
  }

  @Test
  void validateAndExtractUser_WithoutMemberId_Succeeds() {
    String token =
        Jwts.builder()
            .issuer("pulsefit-auth-service")
            .audience().add("pulsefit-api").and()
            .subject("1")
            .claim("email", "admin@pulsefit.com")
            .claim("role", "ADMIN")
            .claim("ver", 1L)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 60000))
            .signWith(privateKey, Jwts.SIG.RS256)
            .compact();

    AuthenticatedUser user = validator.validateAndExtractUser(token);
    assertNotNull(user);
    assertEquals(1L, user.userId());
    assertEquals("ADMIN", user.role());
    assertNull(user.memberId());
  }

  @Test
  void validateAndExtractUser_WithMalformedMemberId_ThrowsException() {
    String token =
        Jwts.builder()
            .issuer("pulsefit-auth-service")
            .audience().add("pulsefit-api").and()
            .subject("10")
            .claim("email", "member@pulsefit.com")
            .claim("role", "MEMBER")
            .claim("memberId", "invalid-string-id")
            .claim("ver", 1L)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 60000))
            .signWith(privateKey, Jwts.SIG.RS256)
            .compact();

    assertThrows(IllegalArgumentException.class, () -> validator.validateAndExtractUser(token));
  }

  private PrivateKey loadPrivateKey(String location) throws Exception {
    Resource resource = new DefaultResourceLoader().getResource(location);
    try (InputStream is = resource.getInputStream()) {
      String keyStr = new String(is.readAllBytes(), StandardCharsets.UTF_8);
      keyStr =
          keyStr
              .replace("-----BEGIN PRIVATE KEY-----", "")
              .replace("-----END PRIVATE KEY-----", "")
              .replaceAll("\\s+", "");
      byte[] decoded = Base64.getDecoder().decode(keyStr);
      PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
      return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
    }
  }
}

