package com.vikisol.one.offboarding.repository;

import com.vikisol.one.offboarding.entity.OffboardingChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OffboardingChecklistItemRepository extends JpaRepository<OffboardingChecklistItem, UUID> {

    List<OffboardingChecklistItem> findByOffboardingCaseIdOrderByCategoryAsc(UUID offboardingCaseId);

    long countByOffboardingCaseIdAndStatus(UUID offboardingCaseId, OffboardingChecklistItem.Status status);
}
