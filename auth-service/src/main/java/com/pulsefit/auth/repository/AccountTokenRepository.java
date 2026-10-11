package com.pulsefit.auth.repository;

import com.pulsefit.auth.model.AccountToken;
import com.pulsefit.auth.model.AccountToken.Purpose;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountTokenRepository extends JpaRepository<AccountToken, Long> {
  Optional<AccountToken> findByTokenHash(String tokenHash);

  Optional<AccountToken> findFirstByEmailAndPurposeAndConsumedAtIsNullAndExpiresAtAfter(
      String email, Purpose purpose, LocalDateTime now);

  List<AccountToken> findByPurposeOrderByCreatedAtDesc(Purpose purpose);

  long countByEmailAndPurposeAndCreatedAtAfter(String email, Purpose purpose, LocalDateTime after);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE AccountToken t SET t.consumedAt = :now "
          + "WHERE t.id = :id AND t.consumedAt IS NULL AND t.expiresAt > :now")
  int consumeIfOpen(@Param("id") Long id, @Param("now") LocalDateTime now);
}
