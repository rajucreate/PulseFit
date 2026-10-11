package com.pulsefit.attendance.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "FACILITY-SERVICE")
public interface FacilityClient {
  @GetMapping("/api/facilities/internal/{id}")
  FacilityView get(@PathVariable("id") Long id);

  record FacilityView(Long id, String name, String status) {}
}
