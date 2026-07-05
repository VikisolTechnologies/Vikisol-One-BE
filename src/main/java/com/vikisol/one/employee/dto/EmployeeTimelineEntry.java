package com.vikisol.one.employee.dto;

import java.time.LocalDateTime;

// One row in an employee's chronological lifecycle activity feed - assembled server-side from
// several already-existing sources (candidate history, BGV, offboarding stage history, audit log)
// rather than stored as its own table. Mirrors the shape/approach of
// RecruitmentService.getCandidateTimeline's CandidateTimelineEntry.
public record EmployeeTimelineEntry(
        LocalDateTime timestamp,
        String category, // RECRUITMENT | BGV | ONBOARDING | OFFBOARDING | AUDIT
        String title,
        String description
) {
}
