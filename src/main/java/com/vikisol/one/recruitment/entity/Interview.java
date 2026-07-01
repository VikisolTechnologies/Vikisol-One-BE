package com.vikisol.one.recruitment.entity;

import com.vikisol.one.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "interviews")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Interview extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id", nullable = false)
    private Candidate candidate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    private UUID interviewerId;
    private String interviewerName;
    private int round;
    private LocalDate scheduledDate;
    private LocalTime scheduledTime;
    private int duration;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Mode mode = Mode.VIDEO;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Status status = Status.SCHEDULED;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    private int rating;

    @Enumerated(EnumType.STRING)
    private Result result;

    public enum Mode {
        IN_PERSON, VIDEO, PHONE
    }

    public enum Status {
        SCHEDULED, COMPLETED, CANCELLED, NO_SHOW
    }

    public enum Result {
        PASS, FAIL, ON_HOLD
    }
}
