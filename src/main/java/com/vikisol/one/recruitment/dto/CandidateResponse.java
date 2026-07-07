package com.vikisol.one.recruitment.dto;

import com.vikisol.one.recruitment.entity.Candidate;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class CandidateResponse {
    private UUID id;
    private String candidateCode;
    private String firstName;
    private String lastName;
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
    private Candidate.EmploymentType employmentType;
    private double experienceYears;
    private double relevantExperienceYears;
    private String certifications;

    private BigDecimal expectedSalary;
    private BigDecimal currentCtc;
    private int noticePeriod;
    private String currentLocation;
    private String preferredLocation;
    private String resumeUrl;
    private String skills;
    private Candidate.Source source;
    private Candidate.Status status;
    private String notes;
    private UUID jobPostingId;
    private String jobPostingTitle;
    // So the candidate's profile can show which tech stack/domain they applied against without a
    // second fetch - previously only the job posting's id/title were exposed here.
    private String jobPostingSkills;
    private String jobPostingDepartment;
    private BigDecimal offeredCtc;
    private UUID offeredDesignationId;
    private String offeredDesignationTitle;
    private UUID offeredDepartmentId;
    private String offeredDepartmentName;
    private java.time.LocalDate offeredDateOfJoining;
    private UUID offeredReportingManagerId;
    private BigDecimal offeredJoiningBonus;
    private BigDecimal offeredVariablePay;
    private String convertedEmployeeId;
    private String managerRemarks;
    private UUID assignedRecruiterId;
    private UUID hiringManagerId;
    private String businessUnit;
    private Candidate.Priority priority;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
