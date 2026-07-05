package com.vikisol.one.offboarding.entity;

import com.vikisol.one.common.entity.BaseEntity;
import com.vikisol.one.employee.entity.Employee;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

// One row per employee exit - created the moment a resignation/termination/retirement is
// recorded, and driven through a fixed stage pipeline until COMPLETED. Mirrors the BGV domain's
// pattern (com.vikisol.one.employee.entity.BackgroundCheck) of a dedicated workflow entity rather
// than folding this into flat booleans on Employee.
@Entity
@Table(name = "offboarding_cases")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class OffboardingCase extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(nullable = false)
    private LocalDate initiatedDate;

    private LocalDate lastWorkingDate;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Stage stage = Stage.RESIGNATION_SUBMITTED;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private CaseStatus status = CaseStatus.IN_PROGRESS;

    // If false, the BGV_EXIT_VERIFICATION stage is skipped when advancing the pipeline.
    @Builder.Default
    private boolean bgvRequired = false;

    private UUID initiatedById;

    public enum Type {
        RESIGNATION, TERMINATION, RETIREMENT
    }

    public enum Stage {
        RESIGNATION_SUBMITTED,
        MANAGER_REVIEW,
        HR_REVIEW,
        KNOWLEDGE_TRANSFER,
        ASSET_COLLECTION,
        IT_CLEARANCE,
        FINANCE_CLEARANCE,
        BGV_EXIT_VERIFICATION,
        FINAL_HR_APPROVAL,
        EXIT_DOCS_GENERATED,
        COMPLETED
    }

    public enum CaseStatus {
        IN_PROGRESS, COMPLETED, CANCELLED
    }
}
