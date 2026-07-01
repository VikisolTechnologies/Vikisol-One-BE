package com.vikisol.one.project.dto;

import com.vikisol.one.project.entity.Project;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class ProjectResponse {
    private UUID id;
    private String name;
    private String code;
    private String description;
    private String clientName;
    private LocalDate startDate;
    private LocalDate endDate;
    private Project.Status status;
    private Project.Priority priority;
    private UUID projectManagerId;
    private BigDecimal budget;
    private boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
