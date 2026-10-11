package com.pulsefit.facility.service;

import com.pulsefit.facility.dto.FacilityRequest;
import com.pulsefit.facility.dto.FacilityResponse;
import com.pulsefit.facility.exception.ResourceNotFoundException;
import com.pulsefit.facility.model.Facility;
import com.pulsefit.facility.model.FacilityStatus;
import com.pulsefit.facility.repository.FacilityRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FacilityService {
  private final FacilityRepository repository;

  public FacilityService(FacilityRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public FacilityResponse create(FacilityRequest request) {
    Facility facility =
        new Facility(
            request.name().trim(),
            request.address().trim(),
            request.location().trim(),
            request.contact().trim(),
            request.operatingHours().trim(),
            request.capacity(),
            request.status());
    return toResponse(repository.save(facility));
  }

  public List<FacilityResponse> search(String name, String location, FacilityStatus status) {
    return repository.search(blankToNull(name), blankToNull(location), status).stream()
        .map(this::toResponse)
        .toList();
  }

  public FacilityResponse get(Long id) {
    return toResponse(require(id));
  }

  @Transactional
  public FacilityResponse update(Long id, FacilityRequest request) {
    Facility facility = require(id);
    facility.update(
        request.name().trim(),
        request.address().trim(),
        request.location().trim(),
        request.contact().trim(),
        request.operatingHours().trim(),
        request.capacity(),
        request.status());
    return toResponse(repository.save(facility));
  }

  @Transactional
  public FacilityResponse deactivate(Long id) {
    Facility facility = require(id);
    facility.deactivate();
    return toResponse(repository.save(facility));
  }

  private Facility require(Long id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Facility not found: " + id));
  }

  private FacilityResponse toResponse(Facility facility) {
    return new FacilityResponse(
        facility.getId(),
        facility.getName(),
        facility.getAddress(),
        facility.getLocation(),
        facility.getContact(),
        facility.getOperatingHours(),
        facility.getCapacity(),
        facility.getStatus());
  }

  private String blankToNull(String value) {
    if (value == null || value.isBlank()) return null;
    return value.trim();
  }
}
