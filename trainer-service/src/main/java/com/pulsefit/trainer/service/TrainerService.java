package com.pulsefit.trainer.service;

import com.pulsefit.trainer.client.RemoteClients.AttendanceClient;
import com.pulsefit.trainer.client.RemoteClients.FacilityClient;
import com.pulsefit.trainer.client.RemoteClients.FacilityView;
import com.pulsefit.trainer.client.RemoteClients.MemberClient;
import com.pulsefit.trainer.client.RemoteClients.MemberView;
import com.pulsefit.trainer.client.RemoteClients.PlanView;
import com.pulsefit.trainer.client.RemoteClients.SubscriptionClient;
import com.pulsefit.trainer.client.RemoteClients.SubscriptionView;
import com.pulsefit.trainer.client.RemoteClients.VisitView;
import com.pulsefit.trainer.dto.TrainerDtos.AssignmentResponse;
import com.pulsefit.trainer.dto.TrainerDtos.RosterMember;
import com.pulsefit.trainer.dto.TrainerDtos.RosterResponse;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrainerService {
  private final TrainerRepository trainers;
  private final AssignmentRepository assignments;
  private final FacilityClient facilities;
  private final MemberClient members;
  private final SubscriptionClient subscriptions;
  private final AttendanceClient attendance;

  public TrainerService(
      TrainerRepository trainers,
      AssignmentRepository assignments,
      FacilityClient facilities,
      MemberClient members) {
    this(trainers, assignments, facilities, members, null, null);
  }

  @Autowired
  public TrainerService(
      TrainerRepository trainers,
      AssignmentRepository assignments,
      FacilityClient facilities,
      MemberClient members,
      SubscriptionClient subscriptions,
      AttendanceClient attendance) {
    this.trainers = trainers;
    this.assignments = assignments;
    this.facilities = facilities;
    this.members = members;
    this.subscriptions = subscriptions;
    this.attendance = attendance;
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

  public boolean isAssigned(String email, Long memberId) {
    if (email == null || email.isBlank() || memberId == null) {
      return false;
    }
    Trainer trainer = trainers.findByEmailIgnoreCase(email.trim()).orElse(null);
    if (trainer == null) {
      return false;
    }
    return assignments.findByTrainerIdAndMemberId(trainer.getId(), memberId).isPresent();
  }

  @Transactional
  public TrainerResponse ensureTrainee(String email, String name, Long facilityId) {
    if (email == null || email.isBlank()) {
      throw new IllegalArgumentException("Trainee email is required");
    }
    String normalized = email.trim().toLowerCase();
    Trainer trainer = trainers.findByEmailIgnoreCase(normalized).orElse(null);
    if (trainer == null) {
      trainer =
          new Trainer(
              name == null || name.isBlank() ? "Trainee Coach" : name.trim(),
              "General training",
              3,
              facilityId == null ? 1L : facilityId,
              "Weekdays",
              TrainerStatus.ACTIVE);
      trainer.attachEmail(normalized);
      trainer = trainers.save(trainer);
    } else if (trainer.getEmail() == null) {
      trainer.attachEmail(normalized);
      trainer = trainers.save(trainer);
    }
    seedAssignments(trainer, normalized);
    return toResponse(trainer);
  }

  public RosterResponse roster(String email) {
    Trainer trainer =
        trainers
            .findByEmailIgnoreCase(email == null ? "" : email.trim())
            .orElseThrow(() -> new ResourceNotFoundException("No trainer profile is linked to this account"));
    List<PlanView> plans = subscriptions == null ? List.of() : subscriptions.plans();
    List<RosterMember> members =
        assignments.findByTrainerId(trainer.getId()).stream()
            .map(assignment -> rosterMember(assignment.getMemberId(), plans))
            .toList();
    return new RosterResponse(trainer.getId(), trainer.getName(), members);
  }

  private void seedAssignments(Trainer trainer, String trainerEmail) {
    if (!assignments.findByTrainerId(trainer.getId()).isEmpty()) {
      return;
    }
    List<MemberView> people;
    try {
      people = members.summaries();
    } catch (FeignException ex) {
      throw new ServiceUnavailableException("Member service is unavailable");
    }
    if (people == null) {
      return;
    }
    Set<Long> taken = new HashSet<>();
    for (TrainerAssignment existing : assignments.findAll()) {
      taken.add(existing.getMemberId());
    }
    int added = 0;
    for (MemberView person : people) {
      if (added >= 3 || person == null || person.id() == null || taken.contains(person.id())) {
        continue;
      }
      if (person.email() != null && person.email().equalsIgnoreCase(trainerEmail)) {
        continue;
      }
      assignments.save(new TrainerAssignment(trainer.getId(), person.id(), OffsetDateTime.now()));
      taken.add(person.id());
      added++;
    }
  }

  private RosterMember rosterMember(Long memberId, List<PlanView> plans) {
    MemberView profile;
    try {
      profile = members.internalGet(memberId);
    } catch (FeignException ex) {
      throw new ServiceUnavailableException("Member service is unavailable");
    }
    List<SubscriptionView> subscriptionsForMember =
        subscriptions == null ? List.of() : subscriptions.byMember(memberId);
    List<VisitView> visits = attendance == null ? List.of() : attendance.byMember(memberId);
    return new RosterMember(
        memberId,
        profile == null ? "Member " + memberId : profile.name(),
        profile == null ? null : profile.email(),
        profile == null ? null : profile.contact(),
        profile == null ? null : profile.status(),
        subscriptionsForMember == null
            ? List.of()
            : subscriptionsForMember.stream()
                .map(row -> toSubscription(row, plans))
                .toList(),
        visits == null
            ? List.of()
            : visits.stream()
                .map(
                    row ->
                        new com.pulsefit.trainer.dto.TrainerDtos.VisitView(
                            row.id(), row.checkInTime(), row.facilityId(), row.accessStatus(), row.denialReason()))
                .toList());
  }

  private com.pulsefit.trainer.dto.TrainerDtos.SubscriptionView toSubscription(
      SubscriptionView row, List<PlanView> plans) {
    PlanView plan =
        plans == null
            ? null
            : plans.stream().filter(item -> item.id() != null && item.id().equals(row.planId())).findFirst().orElse(null);
    return new com.pulsefit.trainer.dto.TrainerDtos.SubscriptionView(
        row.id(),
        row.planId(),
        plan == null ? null : plan.planName(),
        plan == null ? null : plan.durationInDays(),
        row.startDate(),
        row.expiryDate(),
        row.status());
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
