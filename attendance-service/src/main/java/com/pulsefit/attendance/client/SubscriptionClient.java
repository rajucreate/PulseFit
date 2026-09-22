package com.pulsefit.attendance.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "SUBSCRIPTION-SERVICE")
public interface SubscriptionClient {
  @GetMapping("/api/subscriptions/member/{memberId}/valid")
  SubscriptionValidity validity(@PathVariable("memberId") Long memberId);
}
