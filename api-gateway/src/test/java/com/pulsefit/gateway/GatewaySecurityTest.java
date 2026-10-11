package com.pulsefit.gateway;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.pulsefit.gateway.filter.SecurityContextFilter;
import com.pulsefit.gateway.security.JwtTokenValidator;
import com.pulsefit.gateway.security.TokenRevocationValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class GatewaySecurityTest {

  @Mock private TokenRevocationValidator revocationService;
  @Mock private WebFilterChain filterChain;

  private JwtTokenValidator jwtTokenValidator;
  private SecurityContextFilter filter;

  @BeforeEach
  void setUp() {
    jwtTokenValidator = new JwtTokenValidator(new DefaultResourceLoader());
    ReflectionTestUtils.setField(jwtTokenValidator, "publicKeyPath", "classpath:jwt-public.pem");
    ReflectionTestUtils.setField(jwtTokenValidator, "issuer", "pulsefit-auth-service");
    ReflectionTestUtils.setField(jwtTokenValidator, "audience", "pulsefit-api");
    jwtTokenValidator.init();

    filter = new SecurityContextFilter(jwtTokenValidator, revocationService);
    clearInvocations(filterChain);
  }

  @Test
  void publicRoute_PassesThroughWithoutToken() {
    MockServerHttpRequest request =
        MockServerHttpRequest.post("/api/auth/login")
            .header("X-User-Role", "SPOOFED_ADMIN")
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    when(filterChain.filter(any())).thenReturn(Mono.empty());

    filter.filter(exchange, filterChain).block();

    verify(filterChain, times(1)).filter(argThat(ex -> {
      HttpHeaders headers = ex.getRequest().getHeaders();
      return !headers.containsKey("X-User-Role"); // Verify spoofed header stripped
    }));
  }

  @Test
  void invitationAcceptAndPasswordReset_ArePublic() {
    when(filterChain.filter(any())).thenReturn(Mono.empty());
    for (MockServerHttpRequest request : new MockServerHttpRequest[] {
      MockServerHttpRequest.post("/api/auth/invitations/accept").build(),
      MockServerHttpRequest.get("/api/auth/invitations/preview").queryParam("token", "abc").build(),
      MockServerHttpRequest.post("/api/auth/password/forgot").build(),
      MockServerHttpRequest.post("/api/auth/password/reset").build()
    }) {
      filter.filter(MockServerWebExchange.from(request), filterChain).block();
    }
    verify(filterChain, times(4)).filter(any());
  }

  @Test
  void invitationCreate_MissingToken_Returns401() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.post("/api/auth/invitations").build());
    filter.filter(exchange, filterChain).block();
    assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    verify(filterChain, never()).filter(any());
  }

  @Test
  void protectedRoute_MissingToken_Returns401() {
    MockServerHttpRequest request = MockServerHttpRequest.get("/api/members/42").build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    filter.filter(exchange, filterChain).block();

    assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    verify(filterChain, never()).filter(any());
  }

  @Test
  void traineeCannotOpenPaymentsOrChangeSubscriptions() {
    assertEquals(false, authorized("/api/payments", HttpMethod.GET));
    assertEquals(false, authorized("/api/payments/member/4", HttpMethod.GET));
    assertEquals(false, authorized("/api/payments/checkout", HttpMethod.POST));
    assertEquals(false, authorized("/api/subscriptions", HttpMethod.POST));
    assertEquals(false, authorized("/api/subscriptions/9", HttpMethod.PUT));
    assertEquals(false, authorized("/api/attendance/check-in", HttpMethod.POST));
    assertEquals(false, authorized("/api/members", HttpMethod.GET));
    assertEquals(true, authorized("/api/subscriptions/member/4", HttpMethod.GET));
    assertEquals(true, authorized("/api/attendance/member/4", HttpMethod.GET));
    assertEquals(true, authorized("/api/trainers/me/roster", HttpMethod.GET));
    assertEquals(true, authorized("/api/plans", HttpMethod.GET));
  }

  @Test
  void adminStillReachesPayments() {
    assertEquals(
        true,
        ReflectionTestUtils.invokeMethod(filter, "isAuthorized", "/api/payments", HttpMethod.GET, "ADMIN"));
  }

  private boolean authorized(String path, HttpMethod method) {
    return Boolean.TRUE.equals(
        ReflectionTestUtils.invokeMethod(filter, "isAuthorized", path, method, "TRAINEE"));
  }
}
