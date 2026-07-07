package com.vikisol.one.employee.repository;

import com.vikisol.one.employee.entity.EmployeeNominee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EmployeeNomineeRepository extends JpaRepository<EmployeeNominee, UUID> {
    List<EmployeeNominee> findByEmployeeId(UUID employeeId);
    long countByEmployeeId(UUID employeeId);
}
