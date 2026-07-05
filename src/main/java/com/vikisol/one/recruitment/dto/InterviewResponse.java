package com.vikisol.one.recruitment.dto;

import com.vikisol.one.recruitment.entity.Interview;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Data
public class InterviewResponse {
    private UUID id;
    private UUID candidateId;
    private String candidateName;
    private UUID jobPostingId;
    private String jobPostingTitle;

    private String title;
    private Interview.InterviewType type;

    private UUID interviewerId;
    private String interviewerName;
    private List<UUID> additionalInterviewerIds;
    private UUID recruiterId;
    private String recruiterName;
    private UUID hrManagerId;
    private String hrManagerName;

    private int round;
    private int orderIndex;

    private LocalDate scheduledDate;
    private LocalTime scheduledTime;
    private int duration;
    private String timezone;

    private Interview.Mode mode;
    private Interview.Platform platform;
    private String meetingLink;
    private String location;
    private String externalCalendarEventId;
    private String externalMeetingId;
    private String externalTeamsMeetingId;
    private String meetingProviderName;

    private String notes;
    private String agenda;
    private String prepNotes;
    private String attachmentUrls;

    private Interview.Status status;
    private String cancellationReason;

    private String feedback;
    private int rating;
    private Interview.Result result;

    private Interview.Recommendation recommendation;
    private Integer technicalRating;
    private Integer communicationRating;
    private Integer problemSolvingRating;
    private Integer codingRating;
    private Integer architectureRating;
    private Integer cultureFitRating;
    private String strengths;
    private String weaknesses;
    private UUID submittedById;
    private String submittedByName;
    private LocalDateTime submittedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
