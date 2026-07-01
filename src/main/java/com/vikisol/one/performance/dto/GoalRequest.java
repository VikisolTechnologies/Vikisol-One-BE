package com.vikisol.one.performance.dto;

import com.vikisol.one.performance.entity.PerformanceGoal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class GoalRequest {

    @NotNull
    private UUID employeeId;

    @NotNull
    private UUID reviewCycleId;

    @NotBlank
    private String title;

    private String description;

    @NotNull
    private PerformanceGoal.Category category;

    private int weightage;

    private String targetValue;

    private LocalDate dueDate;
}
