package com.vikisol.one.recruitment.dto;

import com.vikisol.one.recruitment.entity.Interview;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class InterviewFeedbackRequest {
    private String feedback;
    @Min(1) @Max(5) private int rating;
    private Interview.Result result;
}
