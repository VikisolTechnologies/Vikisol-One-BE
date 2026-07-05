package com.vikisol.one.employee.repository;

import com.vikisol.one.employee.entity.EmployeeSkill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EmployeeSkillRepository extends JpaRepository<EmployeeSkill, UUID> {
    List<EmployeeSkill> findByEmployeeId(UUID employeeId);
    long countByEmployeeId(UUID employeeId);
}
