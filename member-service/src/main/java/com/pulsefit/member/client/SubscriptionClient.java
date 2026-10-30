package com.pulsefit.member.client;

import com.pulsefit.member.dto.AssignSubscriptionRequest;
import com.pulsefit.member.dto.SubscriptionView;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "SUBSCRIPTION-SERVICE", configuration = com.pulsefit.member.config.FeignClientConfig.class)
public interface SubscriptionClient {
  @GetMapping("/api/subscriptions/member/{memberId}")
  List<SubscriptionView> byMember(@PathVariable("memberId") Long memberId);

  @PostMapping("/api/subscriptions")
  SubscriptionView create(@RequestBody AssignSubscriptionRequest request);
}
