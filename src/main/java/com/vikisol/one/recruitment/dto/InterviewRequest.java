package com.vikisol.one.recruitment.dto;

import com.vikisol.one.recruitment.entity.Interview;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Data
public class InterviewRequest {
    @NotNull private UUID candidateId;
    @NotNull private UUID jobPostingId;

    private String title;
    private Interview.InterviewType type;

    private UUID interviewerId;
    private String interviewerName;
    private List<UUID> additionalInterviewerIds;
    private UUID recruiterId;
    private UUID hrManagerId;

    private int round;
    private int orderIndex;

    @NotNull private LocalDate scheduledDate;
    @NotNull private LocalTime scheduledTime;
    private int duration;
    private String timezone;

    private Interview.Mode mode;
    private Interview.Platform platform;
    private String meetingLink;
    private String location;

    private String notes;
    private String agenda;
    private String prepNotes;
    private String attachmentUrls;
}
