package com.pulsefit.trainer.repository;

import com.pulsefit.trainer.model.Trainer;
import com.pulsefit.trainer.model.TrainerStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrainerRepository extends JpaRepository<Trainer, Long> {
  @Query(
      """
      select t from Trainer t
      where (cast(:facilityId as long) is null or t.facilityId = :facilityId)
        and lower(t.specialization) like lower(concat('%', coalesce(:specialization, ''), '%'))
        and (:status is null or t.status = :status)
      order by t.name asc
      """)
  List<Trainer> search(
      @Param("facilityId") Long facilityId,
      @Param("specialization") String specialization,
      @Param("status") TrainerStatus status);

  List<Trainer> findByFacilityId(Long facilityId);
}
