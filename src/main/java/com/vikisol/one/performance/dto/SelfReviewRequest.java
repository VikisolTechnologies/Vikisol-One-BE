package com.vikisol.one.performance.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class SelfReviewRequest {

    @NotNull
    private UUID reviewCycleId;

    @NotNull
    private Double overallRating;

    private String summary;

    private List<GoalRating> goalRatings;

    @Data
    public static class GoalRating {
        @NotNull
        private UUID goalId;
        private int selfRating;
        private String comments;
    }
}
