package com.vikisol.one.recruitment.dto;

import com.vikisol.one.recruitment.entity.Candidate;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CandidateRequest {
    @NotBlank private String firstName;
    @NotBlank private String lastName;
    @NotBlank @Email private String email;
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
    private String notes;
    private UUID jobPostingId;

    private UUID assignedRecruiterId;
    private UUID hiringManagerId;
    private String businessUnit;
    private Candidate.Priority priority;
}
