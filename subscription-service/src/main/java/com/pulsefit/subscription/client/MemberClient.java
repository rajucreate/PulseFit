package com.pulsefit.subscription.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "MEMBER-SERVICE")
public interface MemberClient {
  @GetMapping("/api/members/{id}")
  MemberClientResponse getById(@PathVariable("id") Long id);
}

