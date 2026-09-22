package com.pulsefit.attendance.repository;

import com.pulsefit.attendance.model.Attendance;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
  List<Attendance> findByMemberId(Long memberId);
}
