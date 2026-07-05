package com.vikisol.one.offboarding.repository;

import com.vikisol.one.offboarding.entity.OffboardingStageHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OffboardingStageHistoryRepository extends JpaRepository<OffboardingStageHistory, UUID> {

    List<OffboardingStageHistory> findByOffboardingCaseIdOrderByChangedAtAsc(UUID offboardingCaseId);
}
