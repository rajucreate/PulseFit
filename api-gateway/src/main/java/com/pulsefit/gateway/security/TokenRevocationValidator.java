package com.pulsefit.gateway.security;

import reactor.core.publisher.Mono;

public interface TokenRevocationValidator {
  Mono<Boolean> isTokenActive(Long userId, Long tokenVersionClaim);
  void invalidateCache(Long userId);
}

