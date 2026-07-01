package com.vikisol.one.timesheet.repository;

import com.vikisol.one.timesheet.entity.TimesheetEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TimesheetEntryRepository extends JpaRepository<TimesheetEntry, UUID> {
    List<TimesheetEntry> findByEmployeeIdAndDateBetween(UUID employeeId, LocalDate start, LocalDate end);
    List<TimesheetEntry> findByEmployeeIdAndStatus(UUID employeeId, TimesheetEntry.Status status);
    List<TimesheetEntry> findByProjectIdAndDateBetween(UUID projectId, LocalDate start, LocalDate end);
    List<TimesheetEntry> findByStatusAndApprovedById(TimesheetEntry.Status status, UUID approverId);
}
