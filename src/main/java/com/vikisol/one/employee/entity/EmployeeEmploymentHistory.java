package com.vikisol.one.employee.entity;

import com.vikisol.one.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

// Prior employer records, entered by the employee during onboarding (or by HR) - distinct from
// this app's own Employee.dateOfJoining/reportingManagerId, which describe employment *at
// Vikisol*, not employment history *before* it.
@Entity
@Table(name = "employee_employment_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EmployeeEmploymentHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(nullable = false)
    private String companyName;

    private String designation;
    private LocalDate joiningDate;
    private LocalDate relievingDate;
    private String skillsUsed;
    private String managerName;
    private String reasonForLeaving;
    private String location;
    private BigDecimal lastSalary;

    private String offerLetterUrl;
    private String experienceLetterUrl;
    private String relievingLetterUrl;
}
