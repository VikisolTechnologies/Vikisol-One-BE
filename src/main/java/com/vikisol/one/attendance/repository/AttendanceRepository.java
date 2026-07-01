package com.vikisol.one.attendance.repository;

import com.vikisol.one.attendance.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, UUID> {

    Optional<Attendance> findByEmployeeIdAndDate(UUID employeeId, LocalDate date);

    List<Attendance> findByEmployeeIdAndDateBetween(UUID employeeId, LocalDate startDate, LocalDate endDate);

    List<Attendance> findByDateAndStatus(LocalDate date, Attendance.AttendanceStatus status);

    long countByEmployeeIdAndStatusAndDateBetween(UUID employeeId, Attendance.AttendanceStatus status, LocalDate startDate, LocalDate endDate);
}
