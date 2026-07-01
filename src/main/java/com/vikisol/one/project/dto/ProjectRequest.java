package com.vikisol.one.project.dto;

import com.vikisol.one.project.entity.Project;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class ProjectRequest {
    @NotBlank private String name;
    @NotBlank private String code;
    private String description;
    private String clientName;
    private LocalDate startDate;
    private LocalDate endDate;
    private Project.Status status;
    private Project.Priority priority;
    private UUID projectManagerId;
    private BigDecimal budget;
}
