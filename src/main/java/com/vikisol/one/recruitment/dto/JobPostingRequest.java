package com.vikisol.one.recruitment.dto;

import com.vikisol.one.recruitment.entity.JobPosting;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class JobPostingRequest {
    @NotBlank private String title;
    private String description;
    @NotNull private UUID departmentId;
    @NotNull private UUID designationId;
    private String location;
    private JobPosting.EmploymentType employmentType;
    private int experienceMin;
    private int experienceMax;
    private BigDecimal salaryMin;
    private BigDecimal salaryMax;
    private String skills;
    private int numberOfPositions;
    private JobPosting.Status status;
    private LocalDate closingDate;
}
