package com.vikisol.one.performance.repository;

import com.vikisol.one.performance.entity.PerformanceGoal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PerformanceGoalRepository extends JpaRepository<PerformanceGoal, UUID> {
    List<PerformanceGoal> findByEmployeeIdAndReviewCycleId(UUID employeeId, UUID cycleId);
    List<PerformanceGoal> findByReviewCycleId(UUID cycleId);
}
