package com.vikisol.one.policy.dto;

import com.vikisol.one.policy.entity.CompanyPolicy;

import java.time.LocalDate;

public record PolicyRequest(
        String title,
        CompanyPolicy.PolicyCategory category,
        String content,
        String version,
        LocalDate effectiveDate,
        Boolean active,
        Boolean requiresAcknowledgement
) {
}
