package com.vikisol.one.attendance.repository;

import com.vikisol.one.attendance.entity.AttendanceRegularization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AttendanceRegularizationRepository extends JpaRepository<AttendanceRegularization, UUID> {

    List<AttendanceRegularization> findByEmployeeId(UUID employeeId);

    List<AttendanceRegularization> findByStatus(AttendanceRegularization.RegularizationStatus status);
}
