package com.vikisol.one.recruitment.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record RescheduleInterviewRequest(
        @NotNull LocalDate scheduledDate,
        @NotNull LocalTime scheduledTime,
        String reason
) {
}
