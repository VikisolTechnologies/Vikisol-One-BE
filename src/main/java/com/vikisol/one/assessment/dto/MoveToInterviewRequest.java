package com.vikisol.one.assessment.dto;

import com.vikisol.one.recruitment.entity.Interview;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record MoveToInterviewRequest(
        @NotNull UUID jobPostingId,
        UUID interviewerId,
        String interviewerName,
        int round,
        @NotNull LocalDate scheduledDate,
        @NotNull LocalTime scheduledTime,
        int duration,
        Interview.Mode mode
) {
}
