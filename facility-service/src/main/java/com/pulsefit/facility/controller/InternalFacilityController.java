package com.pulsefit.facility.controller;

import com.pulsefit.facility.dto.FacilityResponse;
import com.pulsefit.facility.service.FacilityService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/facilities/internal")
public class InternalFacilityController {
  private final FacilityService service;

  public InternalFacilityController(FacilityService service) {
    this.service = service;
  }

  @GetMapping("/{id}")
  public FacilityResponse get(@PathVariable Long id) {
    return service.get(id);
  }
}
