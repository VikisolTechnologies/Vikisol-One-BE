package com.vikisol.one.offboarding.repository;

import com.vikisol.one.offboarding.entity.OffboardingChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OffboardingChecklistItemRepository extends JpaRepository<OffboardingChecklistItem, UUID> {

    List<OffboardingChecklistItem> findByOffboardingCaseIdOrderByCategoryAsc(UUID offboardingCaseId);

    long countByOffboardingCaseIdAndStatus(UUID offboardingCaseId, OffboardingChecklistItem.Status status);

    // Used by the HR Task Center's "Asset Collection Pending" category - pending IT-category items
    // (asset-return checklist rows) that belong to a still-in-progress offboarding case.
    @Query("SELECT i FROM OffboardingChecklistItem i WHERE i.category = 'IT' AND i.status = 'PENDING' " +
            "AND i.offboardingCase.status = com.vikisol.one.offboarding.entity.OffboardingCase.CaseStatus.IN_PROGRESS")
    List<OffboardingChecklistItem> findPendingItAssetItems();
}
