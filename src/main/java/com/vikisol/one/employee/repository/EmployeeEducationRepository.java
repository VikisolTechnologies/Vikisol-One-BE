package com.vikisol.one.employee.repository;

import com.vikisol.one.employee.entity.EmployeeEducation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EmployeeEducationRepository extends JpaRepository<EmployeeEducation, UUID> {
    List<EmployeeEducation> findByEmployeeId(UUID employeeId);
    long countByEmployeeId(UUID employeeId);
}
