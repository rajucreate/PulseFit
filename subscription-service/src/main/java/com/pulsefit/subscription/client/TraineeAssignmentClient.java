package com.pulsefit.subscription.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "TRAINER-SERVICE", contextId = "subscriptionTraineeAssignments")
public interface TraineeAssignmentClient {
  @GetMapping("/api/trainers/internal/assigned")
  AssignmentCheck assigned(@RequestParam("email") String email, @RequestParam("memberId") Long memberId);

  record AssignmentCheck(boolean assigned) {}
}
