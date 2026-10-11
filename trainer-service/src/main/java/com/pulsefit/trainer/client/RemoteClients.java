package com.pulsefit.trainer.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

public class RemoteClients {
  @FeignClient(name = "FACILITY-SERVICE")
  public interface FacilityClient {
    @GetMapping("/api/facilities/internal/{id}")
    FacilityView get(@PathVariable("id") Long id);
  }

  @FeignClient(name = "MEMBER-SERVICE")
  public interface MemberClient {
    @GetMapping("/api/members/{id}")
    MemberView get(@PathVariable("id") Long id);
  }

  public record FacilityView(Long id, String name, String status) {}

  public record MemberView(Long id, String name, String email, String status) {}
}
