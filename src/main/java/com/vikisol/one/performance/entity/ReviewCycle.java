package com.vikisol.one.performance.entity;

import com.vikisol.one.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "review_cycles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ReviewCycle extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Status status = Status.UPCOMING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    public enum Status {
        UPCOMING, ACTIVE, COMPLETED, CANCELLED
    }

    public enum Type {
        QUARTERLY, HALF_YEARLY, ANNUAL
    }
}
