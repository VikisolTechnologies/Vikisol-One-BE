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
    private String currentCompany;
    private String currentDesignation;
    private double experienceYears;
    private BigDecimal expectedSalary;
    private int noticePeriod;
    private String resumeUrl;
    private String skills;
    private Candidate.Source source;
    private String notes;
    private UUID jobPostingId;
}
