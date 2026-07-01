package com.vikisol.one.project.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class ProjectMemberRequest {
    @NotNull private UUID employeeId;
    private String role;
    private int allocationPercentage;
    private LocalDate startDate;
    private LocalDate endDate;
}
