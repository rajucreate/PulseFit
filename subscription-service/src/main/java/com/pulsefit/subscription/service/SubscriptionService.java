package com.pulsefit.subscription.service;

import com.pulsefit.subscription.client.MemberClient;
import com.pulsefit.subscription.dto.*;
import com.pulsefit.subscription.exception.ConflictException;
import com.pulsefit.subscription.exception.ResourceNotFoundException;
import com.pulsefit.subscription.model.*;
import com.pulsefit.subscription.repository.SubscriptionRepository;
import feign.FeignException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionService {
  private final SubscriptionRepository repository;
  private final PlanService plans;
  private final MemberClient memberClient;

  public SubscriptionService(
      SubscriptionRepository repository, PlanService plans, MemberClient memberClient) {
    this.repository = repository;
    this.plans = plans;
    this.memberClient = memberClient;
  }

  public SubscriptionResponse create(SubscriptionRequest r) {
    if (r.status() == SubscriptionStatus.ACTIVE) {
      throw new IllegalArgumentException(
          "A membership becomes active only after the payment is verified.");
    }
    validateDates(r.startDate(), r.expiryDate());
    plans.require(r.planId());
    validateMember(r.memberId());
    return response(
        repository.save(
            new Subscription(r.memberId(), r.planId(), r.startDate(), r.expiryDate(), r.status())));
  }

  @Transactional
  public SubscriptionResponse preparePurchase(Long memberId, Long planId) {
    repository.lockMember(memberId);
    MembershipPlan plan = plans.require(planId);
    if (!plan.isActive()) {
      throw new IllegalArgumentException("This plan is not available");
    }
    validateMember(memberId);
    List<Subscription> rows = repository.findByMemberId(memberId);
    MembershipRules.unexpiredActive(rows, LocalDate.now())
        .ifPresent(
            current -> {
              throw new ConflictException(MembershipRules.blockMessage(current.getExpiryDate()));
            });
    var reusable = MembershipRules.reusablePending(rows, planId);
    if (reusable.isPresent()) {
      return response(reusable.get());
    }
    for (Subscription row : rows) {
      if (row.getStatus() == SubscriptionStatus.PENDING) {
        row.cancel();
        repository.save(row);
      }
    }
    LocalDate start = LocalDate.now();
    LocalDate expiry = start.plusDays(Math.max(plan.getDurationInDays(), 1));
    return response(
        repository.save(new Subscription(memberId, planId, start, expiry, SubscriptionStatus.PENDING)));
  }

  public List<SubscriptionResponse> all() {
    return repository.findAll().stream().map(this::response).toList();
  }

  public SubscriptionResponse get(Long id) {
    return response(require(id));
  }

  public List<SubscriptionResponse> byMember(Long memberId) {
    return repository.findByMemberId(memberId).stream().map(this::response).toList();
  }

  public SubscriptionResponse update(Long id, SubscriptionRequest r) {
    validateDates(r.startDate(), r.expiryDate());
    plans.require(r.planId());
    validateMember(r.memberId());
    Subscription s = require(id);
    if (r.status() == SubscriptionStatus.ACTIVE && s.getStatus() != SubscriptionStatus.ACTIVE) {
      throw new IllegalArgumentException(
          "A membership becomes active only after the payment is verified.");
    }
    s.update(r.memberId(), r.planId(), r.startDate(), r.expiryDate(), r.status());
    return response(repository.save(s));
  }

  public void delete(Long id) {
    if (!repository.existsById(id))
      throw new ResourceNotFoundException("Subscription not found: " + id);
    repository.deleteById(id);
  }

  public ValidityResponse validity(Long memberId) {
    List<Subscription> active =
        repository.findByMemberId(memberId).stream()
            .filter(
                s ->
                    s.getStatus() == SubscriptionStatus.ACTIVE
                        && !s.getExpiryDate().isBefore(LocalDate.now())
                        && !s.getStartDate().isAfter(LocalDate.now()))
            .sorted(
                Comparator.comparing(Subscription::getExpiryDate)
                    .reversed()
                    .thenComparing(Subscription::getId, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();
    if (active.isEmpty()) {
      return new ValidityResponse(memberId, false, null);
    }
    return new ValidityResponse(memberId, true, active.get(0).getId());
  }

  @Transactional
  public SubscriptionResponse activate(Long id) {
    Subscription subscription = require(id);
    if (subscription.getStatus() == SubscriptionStatus.ACTIVE) {
      return response(subscription);
    }
    if (subscription.getStatus() != SubscriptionStatus.PENDING) {
      throw new IllegalArgumentException("Only a pending membership can be activated");
    }
    if (subscription.getExpiryDate().isBefore(LocalDate.now())) {
      throw new IllegalArgumentException("Cannot activate a subscription that has already expired");
    }
    repository.lockMember(subscription.getMemberId());
    List<Subscription> rows = repository.findByMemberId(subscription.getMemberId());
    MembershipRules.unexpiredActive(rows, LocalDate.now())
        .ifPresent(
            current -> {
              throw new ConflictException(MembershipRules.blockMessage(current.getExpiryDate()));
            });
    subscription.activate();
    return response(repository.save(subscription));
  }

  @Transactional
  public SubscriptionResponse cancelPending(Long id) {
    Subscription subscription = require(id);
    if (subscription.getStatus() == SubscriptionStatus.PENDING) {
      subscription.cancel();
      repository.save(subscription);
    }
    return response(subscription);
  }

  public boolean valid(Long memberId) {
    return validity(memberId).valid();
  }

  private void validateDates(LocalDate startDate, LocalDate expiryDate) {
    if (startDate.isAfter(expiryDate)) {
      throw new IllegalArgumentException("Expiry date must be on or after start date");
    }
  }

  private void validateMember(Long memberId) {
    try {
      memberClient.getById(memberId);
    } catch (FeignException.NotFound e) {
      throw new ResourceNotFoundException("Member not found: " + memberId);
    } catch (FeignException e) {
      throw new IllegalStateException("Member service is unavailable");
    }
  }

  private Subscription require(Long id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Subscription not found: " + id));
  }

  private SubscriptionResponse response(Subscription s) {
    return new SubscriptionResponse(
        s.getId(),
        s.getMemberId(),
        s.getPlanId(),
        s.getStartDate(),
        s.getExpiryDate(),
        s.getStatus());
  }
}
