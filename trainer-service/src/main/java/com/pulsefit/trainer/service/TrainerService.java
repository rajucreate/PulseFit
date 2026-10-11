package com.pulsefit.trainer.service;

import com.pulsefit.trainer.client.RemoteClients.FacilityClient;
import com.pulsefit.trainer.client.RemoteClients.FacilityView;
import com.pulsefit.trainer.client.RemoteClients.MemberClient;
import com.pulsefit.trainer.dto.TrainerDtos.AssignmentResponse;
import com.pulsefit.trainer.dto.TrainerDtos.TrainerRequest;
import com.pulsefit.trainer.dto.TrainerDtos.TrainerResponse;
import com.pulsefit.trainer.exception.ConflictException;
import com.pulsefit.trainer.exception.ResourceNotFoundException;
import com.pulsefit.trainer.exception.ServiceUnavailableException;
import com.pulsefit.trainer.model.Trainer;
import com.pulsefit.trainer.model.TrainerAssignment;
import com.pulsefit.trainer.model.TrainerStatus;
import com.pulsefit.trainer.repository.AssignmentRepository;
import com.pulsefit.trainer.repository.TrainerRepository;
import feign.FeignException;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrainerService {
  private final TrainerRepository trainers;
  private final AssignmentRepository assignments;
  private final FacilityClient facilities;
  private final MemberClient members;

  public TrainerService(
      TrainerRepository trainers,
      AssignmentRepository assignments,
      FacilityClient facilities,
      MemberClient members) {
    this.trainers = trainers;
    this.assignments = assignments;
    this.facilities = facilities;
    this.members = members;
  }

  @Transactional
  public TrainerResponse create(TrainerRequest request) {
    requireActiveFacility(request.facilityId());
    Trainer trainer =
        new Trainer(
            request.name().trim(),
            request.specialization().trim(),
            request.experienceYears(),
            request.facilityId(),
            request.availability().trim(),
            request.status());
    return toResponse(trainers.save(trainer));
  }

  public List<TrainerResponse> search(Long facilityId, String specialization, TrainerStatus status) {
    String spec = specialization == null || specialization.isBlank() ? null : specialization.trim();
    return trainers.search(facilityId, spec, status).stream().map(this::toResponse).toList();
  }

  public List<TrainerResponse> byFacility(Long facilityId) {
    return trainers.findByFacilityId(facilityId).stream().map(this::toResponse).toList();
  }

  public TrainerResponse get(Long id) {
    return toResponse(requireTrainer(id));
  }

  @Transactional
  public TrainerResponse update(Long id, TrainerRequest request) {
    requireActiveFacility(request.facilityId());
    Trainer trainer = requireTrainer(id);
    trainer.update(
        request.name().trim(),
        request.specialization().trim(),
        request.experienceYears(),
        request.facilityId(),
        request.availability().trim(),
        request.status());
    return toResponse(trainers.save(trainer));
  }

  @Transactional
  public TrainerResponse deactivate(Long id) {
    Trainer trainer = requireTrainer(id);
    trainer.deactivate();
    return toResponse(trainers.save(trainer));
  }

  @Transactional
  public AssignmentResponse assign(Long trainerId, Long memberId) {
    Trainer trainer = requireTrainer(trainerId);
    if (trainer.getStatus() != TrainerStatus.ACTIVE) {
      throw new IllegalArgumentException("Inactive trainers cannot take new members");
    }
    requireMember(memberId);
    if (assignments.findByTrainerIdAndMemberId(trainerId, memberId).isPresent()) {
      throw new ConflictException("Member is already assigned to this trainer");
    }
    TrainerAssignment saved =
        assignments.save(new TrainerAssignment(trainerId, memberId, OffsetDateTime.now()));
    return toAssignment(saved);
  }

  @Transactional
  public void unassign(Long trainerId, Long memberId) {
    requireTrainer(trainerId);
    if (assignments.findByTrainerIdAndMemberId(trainerId, memberId).isEmpty()) {
      throw new ResourceNotFoundException("Assignment not found");
    }
    assignments.deleteByTrainerIdAndMemberId(trainerId, memberId);
  }

  public List<AssignmentResponse> membersOf(Long trainerId) {
    requireTrainer(trainerId);
    return assignments.findByTrainerId(trainerId).stream().map(this::toAssignment).toList();
  }

  public List<AssignmentResponse> trainersOfMember(Long memberId) {
    return assignments.findByMemberId(memberId).stream().map(this::toAssignment).toList();
  }

  private void requireActiveFacility(Long facilityId) {
    try {
      FacilityView facility = facilities.get(facilityId);
      if (facility == null || facility.status() == null || !"ACTIVE".equalsIgnoreCase(facility.status())) {
        throw new IllegalArgumentException("Facility is not active: " + facilityId);
      }
    } catch (FeignException.NotFound ex) {
      throw new ResourceNotFoundException("Facility not found: " + facilityId);
    } catch (FeignException ex) {
      throw new ServiceUnavailableException("Facility service is unavailable");
    }
  }

  private void requireMember(Long memberId) {
    try {
      members.get(memberId);
    } catch (FeignException.NotFound ex) {
      throw new ResourceNotFoundException("Member not found: " + memberId);
    } catch (FeignException ex) {
      throw new ServiceUnavailableException("Member service is unavailable");
    }
  }

  private Trainer requireTrainer(Long id) {
    return trainers.findById(id).orElseThrow(() -> new ResourceNotFoundException("Trainer not found: " + id));
  }

  private TrainerResponse toResponse(Trainer trainer) {
    return new TrainerResponse(
        trainer.getId(),
        trainer.getName(),
        trainer.getSpecialization(),
        trainer.getExperienceYears(),
        trainer.getFacilityId(),
        trainer.getAvailability(),
        trainer.getStatus());
  }

  private AssignmentResponse toAssignment(TrainerAssignment assignment) {
    return new AssignmentResponse(
        assignment.getId(), assignment.getTrainerId(), assignment.getMemberId(), assignment.getAssignedAt());
  }
}
