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
    private String alternateMobile;
    private String currentAddress;
    private String city;
    private String state;
    private String country;
    private String linkedinUrl;
    private String githubUrl;
    private String portfolioUrl;

    private String currentCompany;
    private String currentDesignation;

    @Enumerated(EnumType.STRING)
    private EmploymentType employmentType;

    private double experienceYears;

    // Boxed (not primitive) - added after this table already had rows, and Hibernate's
    // auto-DDL adds new primitive columns as NOT NULL with no default, which Postgres rejects
    // against existing data ("column contains null values"). Nullable Double avoids that; see
    // mapCandidate()'s null-safe read.
    private Double relevantExperienceYears;

    @Column(columnDefinition = "TEXT")
    private String certifications;

    private BigDecimal expectedSalary;

    // Current-employer CTC - distinct from expectedSalary (what they're asking for) and
    // offeredCtc (what Vikisol proposed/approved). Editable post-creation via a dedicated
    // endpoint that records field-level history (see CandidateFieldChange).
    private BigDecimal currentCtc;
    private int noticePeriod;
    private String currentLocation;
    private String preferredLocation;
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

    // Recruiter-proposed reporting manager, carried through to the created Employee and shown in the offer letter.
    private UUID offeredReportingManagerId;

    private BigDecimal offeredJoiningBonus;
    private BigDecimal offeredVariablePay;

    private String convertedEmployeeId;

    private UUID hiringManagerId;
    private String businessUnit;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Priority priority = Priority.MEDIUM;

    // Set when a manager sends a recruiter's offer proposal back for changes
    @Column(columnDefinition = "TEXT")
    private String managerRemarks;

    private UUID proposedById;

    // Recruiter who owns this candidate (set at creation) - distinct from proposedById, which is
    // only set once an offer is proposed. Needed to scope the recruiter dashboard's per-recruiter
    // widgets correctly instead of showing every recruiter the same global counts.
    private UUID assignedRecruiterId;

    public enum Source {
        PORTAL, REFERRAL, AGENCY, DIRECT, LINKEDIN
    }

    public enum EmploymentType {
        FULL_TIME, PART_TIME, CONTRACT, FREELANCE, INTERN
    }

    public enum Priority {
        LOW, MEDIUM, HIGH, URGENT
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
