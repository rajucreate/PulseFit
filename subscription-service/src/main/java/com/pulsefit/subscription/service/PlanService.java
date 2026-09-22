package com.pulsefit.subscription.service;

import com.pulsefit.subscription.dto.*;
import com.pulsefit.subscription.exception.ResourceNotFoundException;
import com.pulsefit.subscription.model.MembershipPlan;
import com.pulsefit.subscription.repository.MembershipPlanRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PlanService {
  private final MembershipPlanRepository repository;

  public PlanService(MembershipPlanRepository repository) {
    this.repository = repository;
  }

  public MembershipPlanResponse create(MembershipPlanRequest r) {
    return response(
        repository.save(
            new MembershipPlan(
                r.planName(), r.durationInDays(), r.price(), r.description(), r.active())));
  }

  public List<MembershipPlanResponse> all() {
    return repository.findAll().stream().map(this::response).toList();
  }

  public MembershipPlanResponse get(Long id) {
    return response(
        repository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Plan not found: " + id)));
  }

  public MembershipPlan require(Long id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Plan not found: " + id));
  }

  public MembershipPlanResponse update(Long id, MembershipPlanRequest r) {
    MembershipPlan p = require(id);
    p.update(r.planName(), r.durationInDays(), r.price(), r.description(), r.active());
    return response(repository.save(p));
  }

  public void delete(Long id) {
    if (!repository.existsById(id)) throw new ResourceNotFoundException("Plan not found: " + id);
    repository.deleteById(id);
  }

  private MembershipPlanResponse response(MembershipPlan p) {
    return new MembershipPlanResponse(
        p.getId(),
        p.getPlanName(),
        p.getDurationInDays(),
        p.getPrice(),
        p.getDescription(),
        p.isActive());
  }
}
