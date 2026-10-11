package com.pulsefit.subscription.controller;

import com.pulsefit.subscription.dto.SubscriptionResponse;
import com.pulsefit.subscription.service.SubscriptionService;
import org.springframework.web.bind.annotation.*;

record CheckoutCommand(Long memberId, Long planId) {}

@RestController
@RequestMapping("/api/subscriptions/internal")
public class InternalSubscriptionController {
  private final SubscriptionService service;

  public InternalSubscriptionController(SubscriptionService service) {
    this.service = service;
  }

  @PostMapping("/checkout")
  public SubscriptionResponse checkout(@RequestBody CheckoutCommand command) {
    return service.preparePurchase(command.memberId(), command.planId());
  }

  @PostMapping("/{id}/activate")
  public SubscriptionResponse activate(@PathVariable Long id) {
    return service.activate(id);
  }

  @PostMapping("/{id}/cancel")
  public SubscriptionResponse cancel(@PathVariable Long id) {
    return service.cancelPending(id);
  }
}
