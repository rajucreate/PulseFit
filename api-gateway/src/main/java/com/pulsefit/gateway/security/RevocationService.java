package com.pulsefit.gateway.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class RevocationService implements TokenRevocationValidator {

  private final WebClient webClient;
  private final Cache<Long, CachedUserStatus> versionCache;

  public record CachedUserStatus(long tokenVersion, boolean enabled) {}

  public RevocationService(
      WebClient.Builder webClientBuilder,
      ReactorLoadBalancerExchangeFilterFunction lbFunction) {
    this.webClient = webClientBuilder.filter(lbFunction).baseUrl("http://AUTH-SERVICE").build();
    this.versionCache = Caffeine.newBuilder()
        .expireAfterWrite(15, TimeUnit.SECONDS)
        .maximumSize(10_000)
        .build();
  }

  public Mono<Boolean> isTokenActive(Long userId, Long tokenVersionClaim) {
    if (userId == null || tokenVersionClaim == null) {
      return Mono.just(false);
    }

    CachedUserStatus cached = versionCache.getIfPresent(userId);
    if (cached != null) {
      boolean isValid = cached.enabled() && tokenVersionClaim.equals(cached.tokenVersion());
      return Mono.just(isValid);
    }

    return webClient.get()
        .uri("/api/auth/internal/users/{userId}/token-version", userId)
        .retrieve()
        .bodyToMono(Map.class)
        .timeout(Duration.ofMillis(2000))
        .map(map -> {
          Number ver = (Number) map.get("tokenVersion");
          Boolean enabled = (Boolean) map.get("enabled");
          long serverVer = ver != null ? ver.longValue() : -1L;
          boolean isEnabled = enabled != null && enabled;

          versionCache.put(userId, new CachedUserStatus(serverVer, isEnabled));
          return isEnabled && tokenVersionClaim.equals(serverVer);
        })
        .onErrorResume(e -> {
          // Fail-closed policy: If auth-service is unreachable and not in cache, reject
          System.err.println("Revocation check failed (fail-closed applied): " + e.getMessage());
          return Mono.just(false);
        });
  }

  public void invalidateCache(Long userId) {
    if (userId != null) {
      versionCache.invalidate(userId);
    }
  }
}
