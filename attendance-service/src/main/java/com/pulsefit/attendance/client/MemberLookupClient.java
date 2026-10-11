package com.pulsefit.attendance.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "MEMBER-SERVICE", contextId = "attendanceMemberClient")
public interface MemberLookupClient {
  @GetMapping("/api/members/{id}")
  MemberView get(@PathVariable("id") Long id);

  record MemberView(Long id, String name, String email) {}
}

