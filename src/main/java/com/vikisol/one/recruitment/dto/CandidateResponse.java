package com.vikisol.one.recruitment.dto;

import com.vikisol.one.recruitment.entity.Candidate;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class CandidateResponse {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String currentCompany;
    private String currentDesignation;
    private double experienceYears;
    private BigDecimal expectedSalary;
    private int noticePeriod;
    private String resumeUrl;
    private String skills;
    private Candidate.Source source;
    private Candidate.Status status;
    private String notes;
    private UUID jobPostingId;
    private String jobPostingTitle;
    private BigDecimal offeredCtc;
    private UUID offeredDesignationId;
    private String offeredDesignationTitle;
    private java.time.LocalDate offeredDateOfJoining;
    private String convertedEmployeeId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
