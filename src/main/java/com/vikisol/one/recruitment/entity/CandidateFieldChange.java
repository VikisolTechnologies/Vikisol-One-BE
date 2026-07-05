package com.vikisol.one.recruitment.entity;

import com.vikisol.one.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

// Field-level audit history for the small set of candidate fields that change repeatedly during
// the recruitment process (Expected CTC, Current CTC, Notice Period, Current/Preferred Location)
// - a dedicated table rather than the generic AuditLog, since these need to show a clean
// before/after value pair per field on the candidate's own timeline, not a free-text description.
@Entity
@Table(name = "candidate_field_changes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CandidateFieldChange extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id", nullable = false)
    private Candidate candidate;

    @Column(nullable = false)
    private String fieldName;

    @Column(columnDefinition = "TEXT")
    private String previousValue;

    @Column(columnDefinition = "TEXT")
    private String newValue;

    private UUID modifiedById;
    private String modifiedByName;
}
