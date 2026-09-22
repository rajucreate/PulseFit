package com.pulsefit.subscription.repository;

import com.pulsefit.subscription.model.MembershipPlan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipPlanRepository extends JpaRepository<MembershipPlan, Long> {}
