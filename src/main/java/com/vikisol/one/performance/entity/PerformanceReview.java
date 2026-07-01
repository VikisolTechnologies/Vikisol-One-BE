package com.vikisol.one.performance.entity;

import com.vikisol.one.common.entity.BaseEntity;
import com.vikisol.one.employee.entity.Employee;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "performance_reviews")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PerformanceReview extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_cycle_id", nullable = false)
    private ReviewCycle reviewCycle;

    @Column(nullable = false)
    private UUID reviewerId;

    private Double overallSelfRating;

    private Double overallManagerRating;

    @Column(columnDefinition = "TEXT")
    private String selfSummary;

    @Column(columnDefinition = "TEXT")
    private String managerSummary;

    @Column(columnDefinition = "TEXT")
    private String strengths;

    @Column(columnDefinition = "TEXT")
    private String areasOfImprovement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Status status = Status.SELF_REVIEW;

    private LocalDateTime acknowledgedDate;

    public enum Status {
        SELF_REVIEW, MANAGER_REVIEW, COMPLETED, ACKNOWLEDGED
    }
}
