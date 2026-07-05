package com.vikisol.one.recruitment.dto;

import com.vikisol.one.recruitment.entity.Interview;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class InterviewFeedbackRequest {
    // Legacy fields - kept so any existing caller still works.
    private String feedback;
    @Min(1) @Max(5) private int rating;
    private Interview.Result result;

    // Structured feedback (requirement #7).
    private Interview.Recommendation recommendation;
    @Min(1) @Max(10) private Integer technicalRating;
    @Min(1) @Max(10) private Integer communicationRating;
    @Min(1) @Max(10) private Integer problemSolvingRating;
    @Min(1) @Max(10) private Integer codingRating;
    @Min(1) @Max(10) private Integer architectureRating;
    @Min(1) @Max(10) private Integer cultureFitRating;
    private String strengths;
    private String weaknesses;
    private String comments;
}
