package com.pulsefit.auth.repository;

import com.pulsefit.auth.model.ClaimToken;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimTokenRepository extends JpaRepository<ClaimToken, Long> {
  Optional<ClaimToken> findTopByEmailAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
      String email, LocalDateTime now);

  List<ClaimToken> findByEmailOrderByCreatedAtDesc(String email);
}

