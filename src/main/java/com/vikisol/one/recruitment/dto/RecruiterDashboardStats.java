package com.vikisol.one.recruitment.dto;

public record RecruiterDashboardStats(
        long upcomingInterviews,
        long pendingFeedback,
        long offersPending,
        long rejectedCandidates,
        long waitingForScheduling,
        long waitingForHrApproval,
        long waitingForManagerApproval,
        long waitingForOffer
) {
}
