package com.pulsefit.subscription.bootstrap;

import com.pulsefit.subscription.model.MembershipPlan;
import com.pulsefit.subscription.repository.MembershipPlanRepository;
import java.math.BigDecimal;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class PlanCatalogRunner implements CommandLineRunner {
  private final MembershipPlanRepository plans;

  public PlanCatalogRunner(MembershipPlanRepository plans) {
    this.plans = plans;
  }

  @Override
  public void run(String... args) {
    ensure("Weekly", 7, "9.99", "Seven days of club access.");
    ensure("Quarterly", 90, "79.99", "Three months of club access.");
    ensure("Half Year", 180, "149.99", "Six months of club access.");
    ensure("Annual", 365, "249.99", "A full year of club access.");
  }

  private void ensure(String name, int days, String price, String description) {
    boolean exists =
        plans.findAll().stream().anyMatch((plan) -> name.equalsIgnoreCase(plan.getPlanName()));
    if (exists) return;
    plans.save(new MembershipPlan(name, days, new BigDecimal(price), description, true));
  }
}
