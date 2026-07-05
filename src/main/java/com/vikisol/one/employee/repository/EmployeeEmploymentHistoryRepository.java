package com.vikisol.one.employee.repository;

import com.vikisol.one.employee.entity.EmployeeEmploymentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EmployeeEmploymentHistoryRepository extends JpaRepository<EmployeeEmploymentHistory, UUID> {
    List<EmployeeEmploymentHistory> findByEmployeeId(UUID employeeId);
    long countByEmployeeId(UUID employeeId);
}
