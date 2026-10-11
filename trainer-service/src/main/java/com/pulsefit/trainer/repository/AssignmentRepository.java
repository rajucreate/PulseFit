package com.pulsefit.trainer.repository;

import com.pulsefit.trainer.model.TrainerAssignment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssignmentRepository extends JpaRepository<TrainerAssignment, Long> {
  List<TrainerAssignment> findByTrainerId(Long trainerId);

  List<TrainerAssignment> findByMemberId(Long memberId);

  Optional<TrainerAssignment> findByTrainerIdAndMemberId(Long trainerId, Long memberId);

  void deleteByTrainerIdAndMemberId(Long trainerId, Long memberId);
}
