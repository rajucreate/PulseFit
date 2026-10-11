package com.pulsefit.facility.controller;

import com.pulsefit.facility.dto.FacilityRequest;
import com.pulsefit.facility.dto.FacilityResponse;
import com.pulsefit.facility.model.FacilityStatus;
import com.pulsefit.facility.service.FacilityService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/facilities")
public class FacilityController {
  private final FacilityService service;

  public FacilityController(FacilityService service) {
    this.service = service;
  }

  @GetMapping
  public List<FacilityResponse> search(
      @RequestParam(required = false) String name,
      @RequestParam(required = false) String location,
      @RequestParam(required = false) FacilityStatus status) {
    return service.search(name, location, status);
  }

  @GetMapping("/{id}")
  public FacilityResponse get(@PathVariable Long id) {
    return service.get(id);
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  public FacilityResponse create(@Valid @RequestBody FacilityRequest request) {
    return service.create(request);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  public FacilityResponse update(@PathVariable Long id, @Valid @RequestBody FacilityRequest request) {
    return service.update(id, request);
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
  public FacilityResponse deactivate(@PathVariable Long id) {
    return service.deactivate(id);
  }
}
