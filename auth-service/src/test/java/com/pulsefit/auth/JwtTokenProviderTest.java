package com.pulsefit.auth;

import static org.junit.jupiter.api.Assertions.*;

import com.pulsefit.auth.model.Role;
import com.pulsefit.auth.model.User;
import com.pulsefit.auth.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

class JwtTokenProviderTest {

  private JwtTokenProvider jwtTokenProvider;

  @BeforeEach
  void setUp() {
    jwtTokenProvider = new JwtTokenProvider(new DefaultResourceLoader());
    ReflectionTestUtils.setField(jwtTokenProvider, "privateKeyPath", "classpath:jwt-private-test.pem");
    ReflectionTestUtils.setField(jwtTokenProvider, "publicKeyPath", "classpath:jwt-public.pem");
    ReflectionTestUtils.setField(jwtTokenProvider, "issuer", "pulsefit-auth-service");
    ReflectionTestUtils.setField(jwtTokenProvider, "audience", "pulsefit-api");
    ReflectionTestUtils.setField(jwtTokenProvider, "expirationMinutes", 15L);
    jwtTokenProvider.init();
  }

  @Test
  void generateAndValidateToken_Success() {
    User user = new User("alice@example.com", "hashed", "Alice", "Smith", Role.MEMBER, 42L);
    user.setId(101L);
    user.setTokenVersion(1L);

    String token = jwtTokenProvider.generateToken(user);
    assertNotNull(token);

    Claims claims = jwtTokenProvider.validateAndExtractClaims(token);
    assertEquals("101", claims.getSubject());
    assertEquals("alice@example.com", claims.get("email"));
    assertEquals("MEMBER", claims.get("role"));
    assertEquals(42, claims.get("memberId", Integer.class));
    assertEquals(1, claims.get("ver", Integer.class));
    assertEquals("pulsefit-auth-service", claims.getIssuer());
  }

  @Test
  void tamperedSignature_ThrowsSignatureException() {
    User user = new User("bob@example.com", "hashed", "Bob", "Jones", Role.STAFF, null);
    user.setId(102L);

    String token = jwtTokenProvider.generateToken(user);
    String tamperedToken = token.substring(0, token.length() - 5) + "abcde";

    assertThrows(Exception.class, () -> jwtTokenProvider.validateAndExtractClaims(tamperedToken));
  }
}

