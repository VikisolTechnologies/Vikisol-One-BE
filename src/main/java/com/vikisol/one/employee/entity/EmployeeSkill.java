package com.vikisol.one.employee.entity;

import com.vikisol.one.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "employee_skills")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EmployeeSkill extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(nullable = false)
    private String skillName;

    private Double yearsOfExperience;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Level level = Level.INTERMEDIATE;

    private LocalDate lastUsed;

    @Builder.Default
    private boolean certified = false;

    public enum Level {
        BEGINNER, INTERMEDIATE, ADVANCED, EXPERT
    }
}
