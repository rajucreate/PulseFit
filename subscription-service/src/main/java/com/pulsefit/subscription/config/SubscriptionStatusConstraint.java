package com.pulsefit.subscription.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Hibernate's schema update does not widen an existing PostgreSQL enum check when a value is
 * added. PENDING must be allowed or checkout cannot store a membership that is waiting for payment.
 */
@Component
public class SubscriptionStatusConstraint implements ApplicationRunner {
  private final JdbcTemplate jdbc;

  public SubscriptionStatusConstraint(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void run(ApplicationArguments args) {
    jdbc.execute("ALTER TABLE subscriptions DROP CONSTRAINT IF EXISTS subscriptions_status_check");
    jdbc.execute(
        "ALTER TABLE subscriptions ADD CONSTRAINT subscriptions_status_check "
            + "CHECK (status IN ('PENDING', 'ACTIVE', 'EXPIRED', 'CANCELLED'))");
  }
}
