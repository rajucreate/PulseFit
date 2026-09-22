package com.pulsefit.gateway.filter;

import com.pulsefit.gateway.security.JwtTokenValidator;
import com.pulsefit.gateway.security.TokenRevocationValidator;
import io.jsonwebtoken.Claims;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class SecurityContextFilter implements WebFilter, Ordered {

  private final JwtTokenValidator jwtTokenValidator;
  private final TokenRevocationValidator revocationService;

  public SecurityContextFilter(
      JwtTokenValidator jwtTokenValidator, TokenRevocationValidator revocationService) {
    this.jwtTokenValidator = jwtTokenValidator;
    this.revocationService = revocationService;
  }

  @Override
  public int getOrder() {
    return -100;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    ServerHttpRequest request = exchange.getRequest();
    String path = request.getURI().getPath();
    HttpMethod method = request.getMethod();

    // 1. Sanitize incoming request by stripping client-supplied identity headers
    ServerHttpRequest.Builder requestBuilder =
        request.mutate()
            .headers(
                httpHeaders -> {
                  httpHeaders.remove("X-User-Id");
                  httpHeaders.remove("X-User-Role");
                  httpHeaders.remove("X-Member-Id");
                  httpHeaders.remove("X-Caller-Service");
                });

    // 2. Check if route is explicitly public
    if (isPublicRoute(path, method)) {
      return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
    }

    // 3. Extract and validate Bearer token
    String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      return onError(exchange, HttpStatus.UNAUTHORIZED, "AUTH_MISSING_TOKEN", "Missing or malformed Authorization header.");
    }

    String token = authHeader.substring(7).trim();
    Claims claims;
    try {
      claims = jwtTokenValidator.validateAndExtractClaims(token);
    } catch (Exception e) {
      return onError(exchange, HttpStatus.UNAUTHORIZED, "AUTH_INVALID_TOKEN", "Invalid or expired token: " + e.getMessage());
    }

    Long userId = Long.valueOf(claims.getSubject());
    String role = claims.get("role", String.class);
    Number verNum = claims.get("ver", Number.class);
    Long tokenVersion = verNum != null ? verNum.longValue() : 1L;

    Long memberId = null;
    if (claims.get("memberId") != null) {
      memberId = ((Number) claims.get("memberId")).longValue();
    }

    // 4. Token Revocation & Status Check (Fail-Closed)
    final Long finalMemberId = memberId;
    return revocationService
        .isTokenActive(userId, tokenVersion)
        .flatMap(
            isActive -> {
              if (!isActive) {
                return onError(
                    exchange,
                    HttpStatus.UNAUTHORIZED,
                    "AUTH_TOKEN_REVOKED",
                    "Token has been revoked or user account is disabled.");
              }

              // 5. Gateway Route Authorization
              if (!isAuthorized(path, method, role)) {
                return onError(
                    exchange,
                    HttpStatus.FORBIDDEN,
                    "AUTH_FORBIDDEN",
                    "Access denied: Role " + role + " is not authorized for " + method + " " + path);
              }

              // 6. Inject verified identity headers and proceed downstream
              requestBuilder.header("X-User-Id", userId.toString());
              requestBuilder.header("X-User-Role", role);
              requestBuilder.header("X-Caller-Service", "api-gateway");
              if (finalMemberId != null) {
                requestBuilder.header("X-Member-Id", finalMemberId.toString());
              }

              return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
            });
  }

  private boolean isPublicRoute(String path, HttpMethod method) {
    if (path.startsWith("/api/auth/register")
        || path.startsWith("/api/auth/login")
        || path.startsWith("/api/auth/claim/")) {
      return true;
    }
    if (HttpMethod.GET.equals(method) && (path.equals("/api/plans") || path.startsWith("/api/plans/"))) {
      return true;
    }
    return false;
  }

  private boolean isAuthorized(String path, HttpMethod method, String role) {
    // Admin only routes
    if (path.startsWith("/api/auth/users")
        || path.startsWith("/api/auth/audit-logs")
        || (path.startsWith("/api/plans") && !HttpMethod.GET.equals(method))
        || (HttpMethod.DELETE.equals(method) && (path.startsWith("/api/members/") || path.startsWith("/api/subscriptions/")))) {
      return "ADMIN".equalsIgnoreCase(role);
    }

    // Staff or Admin routes (bulk listings)
    if (HttpMethod.GET.equals(method)) {
      if (path.equals("/api/members") || path.equals("/api/subscriptions") || path.equals("/api/attendance")) {
        return "STAFF".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role);
      }
    }

    // Front-desk creation of member profiles
    if (HttpMethod.POST.equals(method) && path.equals("/api/members")) {
      return "STAFF".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role);
    }

    // Member self-service and operations
    return true;
  }

  private Mono<Void> onError(
      ServerWebExchange exchange, HttpStatus status, String errorCode, String message) {
    ServerHttpResponse response = exchange.getResponse();
    response.setStatusCode(status);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

    String json =
        String.format(
            "{\"timestamp\":\"%s\",\"status\":%d,\"error\":\"%s\",\"code\":\"%s\",\"message\":\"%s\",\"path\":\"%s\"}",
            LocalDateTime.now(),
            status.value(),
            status.getReasonPhrase(),
            errorCode,
            message.replace("\"", "'"),
            exchange.getRequest().getURI().getPath());

    DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
    return response.writeWith(Mono.just(buffer));
  }
}
