package com.pulsefit.trainer.controller;

import com.pulsefit.trainer.dto.TrainerDtos.TrainerResponse;
import com.pulsefit.trainer.service.TrainerService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trainers/internal")
public class InternalTrainerController {
  private final TrainerService service;

  public InternalTrainerController(TrainerService service) {
    this.service = service;
  }

  @PostMapping("/bootstrap")
  public TrainerResponse bootstrap(@RequestBody BootstrapRequest request) {
    return service.ensureTrainee(request.email(), request.name(), request.facilityId());
  }

  @GetMapping("/assigned")
  public Map<String, Boolean> assigned(
      @RequestParam("email") String email, @RequestParam("memberId") Long memberId) {
    return Map.of("assigned", service.isAssigned(email, memberId));
  }

  public record BootstrapRequest(String email, String name, Long facilityId) {}
}
