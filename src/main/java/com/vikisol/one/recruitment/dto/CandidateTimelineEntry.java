package com.vikisol.one.recruitment.dto;

import java.time.LocalDateTime;

// One row in a candidate's chronological activity feed - assembled server-side from interviews +
// status/field changes rather than stored as its own table, since it's a read-only derived view
// over data that's already tracked elsewhere (Interview, CandidateFieldChange).
public record CandidateTimelineEntry(
        LocalDateTime timestamp,
        String type, // INTERVIEW_SCHEDULED | INTERVIEW_COMPLETED | INTERVIEW_CANCELLED | FIELD_CHANGED | STATUS_CHANGED | OFFER_GENERATED
        String title,
        String detail,
        String interviewer,
        String recruiter,
        Integer duration,
        String result,
        String notes,
        String feedback,
        Integer rating
) {
}
