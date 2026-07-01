package com.vikisol.one.performance.dto;

import com.vikisol.one.performance.entity.PerformanceReview;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PerformanceReviewResponse {

    private UUID id;
    private UUID employeeId;
    private String employeeName;
    private UUID reviewCycleId;
    private String reviewCycleName;
    private UUID reviewerId;
    private Double overallSelfRating;
    private Double overallManagerRating;
    private String selfSummary;
    private String managerSummary;
    private String strengths;
    private String areasOfImprovement;
    private PerformanceReview.Status status;
    private LocalDateTime acknowledgedDate;
    private List<GoalResponse> goals;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
