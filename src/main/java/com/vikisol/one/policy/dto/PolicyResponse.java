package com.vikisol.one.policy.dto;

import com.vikisol.one.policy.entity.CompanyPolicy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record PolicyResponse(
        UUID id,
        String title,
        CompanyPolicy.PolicyCategory category,
        String content,
        String version,
        LocalDate effectiveDate,
        boolean active,
        boolean requiresAcknowledgement,
        String createdByEmail,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
