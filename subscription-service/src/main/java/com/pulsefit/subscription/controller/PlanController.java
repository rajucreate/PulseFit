package com.pulsefit.subscription.controller;

import com.pulsefit.subscription.dto.*;
import com.pulsefit.subscription.service.PlanService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/plans")
public class PlanController {
  private final PlanService service;

  public PlanController(PlanService service) {
    this.service = service;
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  public MembershipPlanResponse create(@Valid @RequestBody MembershipPlanRequest r) {
    return service.create(r);
  }

  @GetMapping
  public List<MembershipPlanResponse> all() {
    return service.all();
  }

  @GetMapping("/{id}")
  public MembershipPlanResponse get(@PathVariable Long id) {
    return service.get(id);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public MembershipPlanResponse update(
      @PathVariable Long id, @Valid @RequestBody MembershipPlanRequest r) {
    return service.update(id, r);
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    service.delete(id);
  }
}
