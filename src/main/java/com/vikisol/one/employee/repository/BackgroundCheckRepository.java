package com.vikisol.one.employee.repository;

import com.vikisol.one.employee.entity.BackgroundCheck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BackgroundCheckRepository extends JpaRepository<BackgroundCheck, UUID> {
    List<BackgroundCheck> findByEmployeeId(UUID employeeId);
    List<BackgroundCheck> findByStatus(BackgroundCheck.Status status);
}
