package com.vikisol.one.recruitment.dto;

import com.vikisol.one.recruitment.entity.JobPosting;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class JobPostingResponse {
    private UUID id;
    private String title;
    private String description;
    private UUID departmentId;
    private String departmentName;
    private UUID designationId;
    private String designationTitle;
    private String location;
    private JobPosting.EmploymentType employmentType;
    private int experienceMin;
    private int experienceMax;
    private BigDecimal salaryMin;
    private BigDecimal salaryMax;
    private String skills;
    private int numberOfPositions;
    private JobPosting.Status status;
    private UUID postedById;
    private LocalDate postedDate;
    private LocalDate closingDate;
    private boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
