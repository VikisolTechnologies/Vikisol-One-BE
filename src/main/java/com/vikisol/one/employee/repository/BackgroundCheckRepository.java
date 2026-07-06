package com.vikisol.one.employee.repository;

import com.vikisol.one.employee.entity.BackgroundCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface BackgroundCheckRepository extends JpaRepository<BackgroundCheck, UUID> {
    List<BackgroundCheck> findByEmployeeId(UUID employeeId);
    List<BackgroundCheck> findByStatus(BackgroundCheck.Status status);

    // Used by the HR Task Center's "BGV Pending" category - checks not yet in a terminal state,
    // for currently active employees only.
    @Query("SELECT bc FROM BackgroundCheck bc WHERE bc.status NOT IN ('APPROVED','REJECTED') AND bc.employee.isActive = true")
    List<BackgroundCheck> findPendingForActiveEmployees();
}
