package com.vikisol.one.recruitment.dto;

import com.vikisol.one.recruitment.entity.Interview;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
public class InterviewRequest {
    @NotNull private UUID candidateId;
    @NotNull private UUID jobPostingId;
    private UUID interviewerId;
    private String interviewerName;
    private int round;
    @NotNull private LocalDate scheduledDate;
    @NotNull private LocalTime scheduledTime;
    private int duration;
    private Interview.Mode mode;
}
