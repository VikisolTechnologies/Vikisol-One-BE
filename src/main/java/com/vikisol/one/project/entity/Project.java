package com.vikisol.one.project.entity;

import com.vikisol.one.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "projects")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Project extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String clientName;
    private LocalDate startDate;
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Status status = Status.NOT_STARTED;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Priority priority = Priority.MEDIUM;

    private UUID projectManagerId;
    private BigDecimal budget;

    @Builder.Default
    private boolean isActive = true;

    public enum Status {
        NOT_STARTED, IN_PROGRESS, ON_HOLD, COMPLETED, CANCELLED
    }

    public enum Priority {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}
