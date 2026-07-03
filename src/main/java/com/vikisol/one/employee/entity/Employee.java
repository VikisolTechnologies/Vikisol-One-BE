package com.vikisol.one.employee.entity;

import com.vikisol.one.auth.entity.User;
import com.vikisol.one.common.entity.BaseEntity;
import com.vikisol.one.department.entity.Department;
import com.vikisol.one.designation.entity.Designation;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "employees")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Employee extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String employeeId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false)
    private String email;

    private String phone;

    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "designation_id")
    private Designation designation;

    private LocalDate dateOfJoining;
    private LocalDate probationEndDate;
    private LocalDate confirmationDate;

    private UUID reportingManagerId;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private EmploymentType employmentType = EmploymentType.FULL_TIME;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private EmploymentStatus employmentStatus = EmploymentStatus.ACTIVE;

    private String currentAddress;
    private String permanentAddress;
    private String city;
    private String state;
    private String country;
    private String pincode;

    private String bankName;
    private String bankAccountNumber;
    private String ifscCode;
    private String panNumber;
    private String aadharNumber;
    private String uanNumber;
    private String pfNumber;
    private String esiNumber;

    private String emergencyContactName;
    private String emergencyContactPhone;
    private String emergencyContactRelation;

    private String profilePictureUrl;

    private BigDecimal basicSalary;
    private BigDecimal hra;
    private BigDecimal conveyanceAllowance;
    private BigDecimal medicalAllowance;
    private BigDecimal specialAllowance;
    // CEO-nameable 6th CTC component (see PayrollService.CUSTOM_LABEL_KEY for its display name) - nullable/0 by default.
    private BigDecimal customAllowance;
    private BigDecimal grossSalary;
    private BigDecimal ctc;

    @Builder.Default
    private boolean isActive = true;

    // Onboarding checklist - tracked here rather than a separate table since it's just a
    // handful of one-time flags per employee, not something that needs its own history/queries.
    @Column(columnDefinition = "boolean default false")
    @Builder.Default
    private boolean onboardingDocumentsVerified = false;

    @Column(columnDefinition = "boolean default false")
    @Builder.Default
    private boolean onboardingAssetsAssigned = false;

    @Column(columnDefinition = "boolean default false")
    @Builder.Default
    private boolean onboardingBankDetailsCollected = false;

    @Column(columnDefinition = "boolean default false")
    @Builder.Default
    private boolean onboardingInductionCompleted = false;

    public enum Gender {
        MALE, FEMALE, OTHER
    }

    public enum EmploymentType {
        FULL_TIME, PART_TIME, CONTRACT, INTERN
    }

    public enum EmploymentStatus {
        ACTIVE, ON_NOTICE, TERMINATED, RESIGNED, ABSCONDED
    }
}
