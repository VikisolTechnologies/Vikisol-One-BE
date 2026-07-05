package com.vikisol.one.employee.entity;

import com.vikisol.one.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

// One row per (employee, checkType) - a dedicated BGV domain rather than folding this into the
// existing 4 flat onboarding booleans, since BGV needs a real per-item workflow status (not just
// done/not-done) and its own reviewer/remarks/timestamp trail.
@Entity
@Table(name = "background_checks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class BackgroundCheck extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CheckType checkType;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Status status = Status.PENDING;

    private String remarks;
    private UUID reviewedById;
    private LocalDateTime reviewedAt;

    public enum CheckType {
        IDENTITY, EDUCATION, EMPLOYMENT, ADDRESS, REFERENCE, POLICE, DRUG_TEST, VISA
    }

    public enum Status {
        PENDING, SUBMITTED, IN_REVIEW, APPROVED, REJECTED
    }
}
