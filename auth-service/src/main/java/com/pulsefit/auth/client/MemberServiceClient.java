package com.pulsefit.auth.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "MEMBER-SERVICE")
public interface MemberServiceClient {
  @GetMapping("/api/members/internal/by-email")
  MemberDto findByEmail(@RequestParam("email") String email);

  @PostMapping("/api/members/internal/create")
  MemberDto createMember(@RequestBody CreateMemberRequest request);
}

