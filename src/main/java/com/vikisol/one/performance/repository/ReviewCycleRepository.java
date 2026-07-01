package com.vikisol.one.performance.repository;

import com.vikisol.one.performance.entity.ReviewCycle;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ReviewCycleRepository extends JpaRepository<ReviewCycle, UUID> {
    List<ReviewCycle> findByStatus(ReviewCycle.Status status);
}
