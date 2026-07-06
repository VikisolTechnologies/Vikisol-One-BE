package com.vikisol.one.performance.repository;

import com.vikisol.one.performance.entity.PerformanceReview;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PerformanceReviewRepository extends JpaRepository<PerformanceReview, UUID> {
    Optional<PerformanceReview> findByEmployeeIdAndReviewCycleId(UUID employeeId, UUID cycleId);
    List<PerformanceReview> findByReviewerIdAndReviewCycleId(UUID reviewerId, UUID cycleId);
    List<PerformanceReview> findByReviewCycleIdAndStatus(UUID cycleId, PerformanceReview.Status status);
    List<PerformanceReview> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);
}
