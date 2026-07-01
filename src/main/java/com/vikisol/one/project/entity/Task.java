package com.vikisol.one.project.entity;

import com.vikisol.one.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tasks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Task extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private UUID assigneeId;
    private String assigneeName;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Status status = Status.TODO;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Priority priority = Priority.MEDIUM;

    private LocalDate startDate;
    private LocalDate dueDate;
    private LocalDate completedDate;
    private double estimatedHours;
    private double actualHours;
    private UUID parentTaskId;

    public enum Status {
        TODO, IN_PROGRESS, IN_REVIEW, DONE, BLOCKED
    }

    public enum Priority {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}
