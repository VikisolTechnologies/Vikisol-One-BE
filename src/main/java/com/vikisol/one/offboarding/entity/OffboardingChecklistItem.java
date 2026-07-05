package com.vikisol.one.offboarding.entity;

import com.vikisol.one.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "offboarding_checklist_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class OffboardingChecklistItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offboarding_case_id", nullable = false)
    private OffboardingCase offboardingCase;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Column(nullable = false)
    private String label;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Status status = Status.PENDING;

    private UUID completedById;
    private LocalDateTime completedAt;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    // Set only for dynamic per-asset IT items (see OffboardingService.seedChecklist) - links this
    // checklist row back to the AssetAssignment it represents so marking it Completed can flip the
    // underlying assignment/asset back to returned/AVAILABLE. Null for the generic HR/Finance/
    // Manager/access items that aren't tied to a physical asset.
    private UUID assetAssignmentId;

    public enum Category {
        HR, IT, FINANCE, MANAGER
    }

    public enum Status {
        PENDING, COMPLETED
    }
}
