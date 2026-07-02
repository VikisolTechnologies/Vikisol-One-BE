package com.vikisol.one.recruitment.entity;

import com.vikisol.one.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "candidates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Candidate extends BaseEntity {

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    private String phone;
    private String currentCompany;
    private String currentDesignation;
    private double experienceYears;
    private BigDecimal expectedSalary;
    private int noticePeriod;
    private String resumeUrl;

    @Column(columnDefinition = "TEXT")
    private String skills;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Source source = Source.DIRECT;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Status status = Status.NEW;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_id")
    private JobPosting jobPosting;

    // Set when HR selects the candidate and generates the offer
    private BigDecimal offeredCtc;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offered_designation_id")
    private com.vikisol.one.designation.entity.Designation offeredDesignation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offered_department_id")
    private com.vikisol.one.department.entity.Department offeredDepartment;

    private java.time.LocalDate offeredDateOfJoining;

    private String convertedEmployeeId;

    // Set when a manager sends a recruiter's offer proposal back for changes
    @Column(columnDefinition = "TEXT")
    private String managerRemarks;

    private UUID proposedById;

    public enum Source {
        PORTAL, REFERRAL, AGENCY, DIRECT, LINKEDIN
    }

    public enum Status {
        NEW, SCREENING, SHORTLISTED, INTERVIEW_SCHEDULED, INTERVIEWED,
        // A recruiter proposed CTC/designation/department and is waiting on manager sign-off.
        PENDING_APPROVAL,
        // A manager sent the proposal back to the recruiter with remarks to fix.
        REVISION_REQUESTED,
        // Manager-approved: employee record created and offer letter emailed.
        SELECTED, OFFER_MADE, OFFER_ACCEPTED, OFFER_DECLINED, JOINED, REJECTED
    }
}
