package com.vikisol.one.performance.entity;

import com.vikisol.one.common.entity.BaseEntity;
import com.vikisol.one.employee.entity.Employee;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "performance_goals")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PerformanceGoal extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_cycle_id", nullable = false)
    private ReviewCycle reviewCycle;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    private int weightage;

    private String targetValue;

    private String achievedValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Status status = Status.NOT_STARTED;

    private LocalDate dueDate;

    private Integer selfRating;

    private Integer managerRating;

    @Column(columnDefinition = "TEXT")
    private String selfComments;

    @Column(columnDefinition = "TEXT")
    private String managerComments;

    public enum Category {
        BUSINESS, TECHNICAL, BEHAVIORAL, LEARNING
    }

    public enum Status {
        NOT_STARTED, IN_PROGRESS, COMPLETED, DEFERRED
    }
}
