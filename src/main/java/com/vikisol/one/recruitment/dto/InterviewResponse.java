package com.vikisol.one.recruitment.dto;

import com.vikisol.one.recruitment.entity.Interview;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Data
public class InterviewResponse {
    private UUID id;
    private UUID candidateId;
    private String candidateName;
    private UUID jobPostingId;
    private String jobPostingTitle;
    private UUID interviewerId;
    private String interviewerName;
    private int round;
    private LocalDate scheduledDate;
    private LocalTime scheduledTime;
    private int duration;
    private Interview.Mode mode;
    private Interview.Status status;
    private String feedback;
    private int rating;
    private Interview.Result result;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
