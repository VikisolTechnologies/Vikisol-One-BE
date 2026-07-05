package com.vikisol.one.assessment.entity;

import com.vikisol.one.common.entity.BaseEntity;
import com.vikisol.one.recruitment.entity.Candidate;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "assessments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Assessment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id")
    private Candidate candidate;

    @Column(nullable = false)
    private String candidateName;

    @Column(nullable = false)
    private String candidateEmail;

    private String candidatePhone;

    private double yearsOfExperience;

    @Column(columnDefinition = "TEXT")
    private String techStack;

    private String resumeUrl;

    @Column(nullable = false)
    private String testName;

    @Column(nullable = false)
    private LocalDateTime dateTaken;

    private double score;

    private double maxScore;

    private double percentage;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Status status = Status.PENDING_REVIEW;

    // Unique id of the submission on the Arena side - used to make webhook ingestion idempotent
    // if Arena retries the callback.
    @Column(unique = true)
    private String arenaSubmissionId;

    @Builder.Default
    private boolean movedToInterview = false;

    public enum Status {
        PASS, FAIL, PENDING_REVIEW
    }
}
