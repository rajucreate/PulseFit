package com.pulsefit.facility.repository;

import com.pulsefit.facility.model.Facility;
import com.pulsefit.facility.model.FacilityStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FacilityRepository extends JpaRepository<Facility, Long> {
  @Query(
      """
      select f from Facility f
      where lower(f.name) like lower(concat('%', coalesce(:name, ''), '%'))
        and lower(f.location) like lower(concat('%', coalesce(:location, ''), '%'))
        and (:status is null or f.status = :status)
      order by f.name asc
      """)
  List<Facility> search(
      @Param("name") String name,
      @Param("location") String location,
      @Param("status") FacilityStatus status);
}
