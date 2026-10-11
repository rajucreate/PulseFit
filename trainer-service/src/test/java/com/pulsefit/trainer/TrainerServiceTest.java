package com.pulsefit.trainer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pulsefit.trainer.client.RemoteClients.FacilityClient;
import com.pulsefit.trainer.client.RemoteClients.FacilityView;
import com.pulsefit.trainer.client.RemoteClients.MemberClient;
import com.pulsefit.trainer.client.RemoteClients.MemberView;
import com.pulsefit.trainer.dto.TrainerDtos.TrainerRequest;
import com.pulsefit.trainer.exception.ConflictException;
import com.pulsefit.trainer.model.Trainer;
import com.pulsefit.trainer.model.TrainerStatus;
import com.pulsefit.trainer.repository.AssignmentRepository;
import com.pulsefit.trainer.repository.TrainerRepository;
import com.pulsefit.trainer.service.TrainerService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TrainerServiceTest {
  @Mock private TrainerRepository trainers;
  @Mock private AssignmentRepository assignments;
  @Mock private FacilityClient facilities;
  @Mock private MemberClient members;
  private TrainerService service;

  @BeforeEach
  void setUp() {
    service = new TrainerService(trainers, assignments, facilities, members);
  }

  @Test
  void deactivateDoesNotDeleteTheTrainer() {
    Trainer trainer = new Trainer("Ada", "Strength", 5, 1L, "Mornings", TrainerStatus.ACTIVE);
    when(trainers.findById(8L)).thenReturn(Optional.of(trainer));
    when(trainers.save(trainer)).thenReturn(trainer);
    assertEquals(TrainerStatus.INACTIVE, service.deactivate(8L).status());
    verify(trainers, never()).delete(any());
  }

  @Test
  void duplicateAssignmentIsRejected() {
    Trainer trainer = new Trainer("Ada", "Strength", 5, 1L, "Mornings", TrainerStatus.ACTIVE);
    when(trainers.findById(8L)).thenReturn(Optional.of(trainer));
    when(members.get(3L)).thenReturn(new MemberView(3L, "Ann", "a@b.com", "555", "ACTIVE"));
    when(assignments.findByTrainerIdAndMemberId(8L, 3L)).thenReturn(Optional.of(mock()));
    assertThrows(ConflictException.class, () -> service.assign(8L, 3L));
  }

  @Test
  void createRequiresAnActiveFacility() {
    when(facilities.get(1L)).thenReturn(new FacilityView(1L, "North", "INACTIVE"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.create(
                new TrainerRequest("Ada", "Strength", 5, 1L, "Mornings", TrainerStatus.ACTIVE)));
  }

  private com.pulsefit.trainer.model.TrainerAssignment mock() {
    return new com.pulsefit.trainer.model.TrainerAssignment(8L, 3L, java.time.OffsetDateTime.now());
  }
}
