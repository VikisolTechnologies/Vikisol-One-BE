package com.vikisol.one.recruitment.entity;

import com.vikisol.one.common.entity.BaseEntity;
import com.vikisol.one.department.entity.Department;
import com.vikisol.one.designation.entity.Designation;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "job_postings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class JobPosting extends BaseEntity {

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "designation_id")
    private Designation designation;

    private String location;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private EmploymentType employmentType = EmploymentType.FULL_TIME;

    private int experienceMin;
    private int experienceMax;
    private BigDecimal salaryMin;
    private BigDecimal salaryMax;

    @Column(columnDefinition = "TEXT")
    private String skills;

    @Builder.Default
    private int numberOfPositions = 1;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Status status = Status.DRAFT;

    private UUID postedById;
    private LocalDate postedDate;
    private LocalDate closingDate;

    @Builder.Default
    private boolean isActive = true;

    public enum EmploymentType {
        FULL_TIME, PART_TIME, CONTRACT, INTERN
    }

    public enum Status {
        DRAFT, OPEN, ON_HOLD, CLOSED, FILLED
    }
}
