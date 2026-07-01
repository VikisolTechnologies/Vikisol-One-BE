package com.vikisol.one.performance.dto;

import com.vikisol.one.performance.entity.PerformanceGoal;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class GoalResponse {

    private UUID id;
    private UUID employeeId;
    private String employeeName;
    private UUID reviewCycleId;
    private String reviewCycleName;
    private String title;
    private String description;
    private PerformanceGoal.Category category;
    private int weightage;
    private String targetValue;
    private String achievedValue;
    private PerformanceGoal.Status status;
    private LocalDate dueDate;
    private Integer selfRating;
    private Integer managerRating;
    private String selfComments;
    private String managerComments;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
