package com.vikisol.one.policy.dto;

import java.time.LocalDateTime;

// The current user's acknowledgement status for a single policy - "NOT_VIEWED"/"VIEWED"/"ACCEPTED".
public record AcknowledgementStatusResponse(
        String status,
        LocalDateTime viewedAt,
        LocalDateTime acceptedAt
) {
}
