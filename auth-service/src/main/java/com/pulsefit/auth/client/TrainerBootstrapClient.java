package com.pulsefit.auth.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "TRAINER-SERVICE", contextId = "authTrainerBootstrap")
public interface TrainerBootstrapClient {
  @PostMapping("/api/trainers/internal/bootstrap")
  void bootstrap(@RequestBody BootstrapRequest request);

  record BootstrapRequest(String email, String name, Long facilityId) {}
}
