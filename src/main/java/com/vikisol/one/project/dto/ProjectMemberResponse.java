package com.vikisol.one.project.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class ProjectMemberResponse {
    private UUID id;
    private UUID projectId;
    private String projectName;
    private UUID employeeId;
    private String employeeName;
    private String role;
    private int allocationPercentage;
    private LocalDate startDate;
    private LocalDate endDate;
    private boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
