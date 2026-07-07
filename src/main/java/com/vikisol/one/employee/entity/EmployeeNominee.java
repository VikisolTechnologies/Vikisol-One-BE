package com.vikisol.one.employee.entity;

import com.vikisol.one.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

// A proper one-to-many nominee list (Provident Fund/insurance nominations commonly require more
// than one person with a split share) - the older flat nomineeName/nomineeRelation/... fields on
// Employee itself only ever supported a single nominee and are kept as-is for backward
// compatibility with existing HR-entered data, but the self-service Onboarding Wizard now manages
// nominees through this list instead.
@Entity
@Table(name = "employee_nominees")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EmployeeNominee extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String relation;

    private LocalDate dateOfBirth;
    private Integer sharePercentage;

    @Enumerated(EnumType.STRING)
    private Employee.Gender gender;
}
