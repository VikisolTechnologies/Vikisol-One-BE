package com.vikisol.one.offboarding.entity;

import com.vikisol.one.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

// Timeline/audit trail of every stage transition on an OffboardingCase - who moved it, when,
// from which stage to which, with optional comments. Kept as its own table (rather than reusing
// the generic AuditLog) because the case-detail timeline UI needs structured from/to stage data,
// not just a free-text audit line (a matching AuditService.record(...) call is still made too).
@Entity
@Table(name = "offboarding_stage_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class OffboardingStageHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offboarding_case_id", nullable = false)
    private OffboardingCase offboardingCase;

    @Enumerated(EnumType.STRING)
    private OffboardingCase.Stage fromStage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OffboardingCase.Stage toStage;

    private UUID changedById;

    @Column(nullable = false)
    private LocalDateTime changedAt;

    @Column(columnDefinition = "TEXT")
    private String comments;
}
