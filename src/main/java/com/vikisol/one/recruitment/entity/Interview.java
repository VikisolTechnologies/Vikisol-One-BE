package com.vikisol.one.recruitment.entity;

import com.vikisol.one.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "interviews")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Interview extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id", nullable = false)
    private Candidate candidate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    private String title;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private InterviewType type = InterviewType.CUSTOM;

    // Primary interviewer - kept as the pre-existing scalar fields (not a hard FK) since an
    // interviewer isn't always an Employee (client-round interviewers, external panelists).
    private UUID interviewerId;
    private String interviewerName;

    // Additional interviewers beyond the primary - a simple id list is enough for CC'ing them on
    // the invite and showing them on the timeline; they don't need their own feedback workflow
    // distinct from the primary interviewer's.
    @ElementCollection
    @CollectionTable(name = "interview_additional_interviewers", joinColumns = @JoinColumn(name = "interview_id"))
    @Column(name = "interviewer_id")
    @Builder.Default
    private List<UUID> additionalInterviewerIds = new java.util.ArrayList<>();

    private UUID recruiterId;
    private UUID hrManagerId;

    // Free-form int (not an enum) so there is no hardcoded cap on how many rounds a pipeline can
    // have; `orderIndex` is what the frontend uses to reorder a candidate's rounds independently
    // of when each round happened to be scheduled.
    private int round;

    // Boxed - new column on a table that already had rows; see Candidate.relevantExperienceYears
    // for why this must be nullable instead of a NOT-NULL-by-default primitive.
    private Integer orderIndex;

    private LocalDate scheduledDate;
    private LocalTime scheduledTime;
    private int duration;
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Mode mode = Mode.VIDEO;

    // Specific platform (Google Meet/Teams/Zoom/In Person/Phone Call) - more granular than `mode`
    // (kept for backward compatibility with existing IN_PERSON/VIDEO/PHONE filtering).
    @Enumerated(EnumType.STRING)
    private Platform platform;

    // Manually-entered link (still populated as a fallback when no MeetingProvider is configured,
    // or mirrors the provider's joinUrl once created for display purposes).
    private String meetingLink;
    private String location;

    // Populated once a real MeetingProvider (e.g. Microsoft 365/Teams) creates the meeting -
    // provider-neutral names per the abstraction layer, so swapping providers later doesn't need
    // a schema change. Null when running on the Noop fallback (manual meeting link).
    private String externalCalendarEventId;
    private String externalMeetingId;
    private String externalTeamsMeetingId;
    private String meetingProviderName;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(columnDefinition = "TEXT")
    private String agenda;

    @Column(columnDefinition = "TEXT")
    private String prepNotes;

    // Comma-separated list of stored file URLs (consistent with how Candidate.skills is a plain
    // delimited string rather than its own table - attachments here are a handful of reference
    // files, not something that needs independent querying).
    @Column(columnDefinition = "TEXT")
    private String attachmentUrls;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Status status = Status.SCHEDULED;

    private String cancellationReason;

    // ─── Feedback (legacy single-value fields, kept for backward compatibility) ───
    @Column(columnDefinition = "TEXT")
    private String feedback;

    private int rating;

    @Enumerated(EnumType.STRING)
    private Result result;

    // ─── Feedback (structured, per requirement #7) ───
    @Enumerated(EnumType.STRING)
    private Recommendation recommendation;

    private Integer technicalRating;
    private Integer communicationRating;
    private Integer problemSolvingRating;
    private Integer codingRating;
    private Integer architectureRating;
    private Integer cultureFitRating;

    @Column(columnDefinition = "TEXT")
    private String strengths;

    @Column(columnDefinition = "TEXT")
    private String weaknesses;

    private UUID submittedById;
    private LocalDateTime submittedAt;

    public enum InterviewType {
        HR, TECHNICAL_L1, TECHNICAL_L2, TECHNICAL_L3, MANAGERIAL, CLIENT, FINAL_HR, CEO_ROUND, CUSTOM
    }

    public enum Mode {
        IN_PERSON, VIDEO, PHONE
    }

    public enum Platform {
        GOOGLE_MEET, MICROSOFT_TEAMS, ZOOM, IN_PERSON, PHONE_CALL
    }

    public enum Status {
        SCHEDULED, COMPLETED, CANCELLED, NO_SHOW, RESCHEDULED
    }

    public enum Result {
        PASS, FAIL, ON_HOLD
    }

    public enum Recommendation {
        STRONG_HIRE, HIRE, HOLD, REJECT, STRONG_REJECT
    }
}
