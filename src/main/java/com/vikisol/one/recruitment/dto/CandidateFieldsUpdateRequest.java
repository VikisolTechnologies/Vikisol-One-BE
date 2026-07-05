package com.vikisol.one.recruitment.dto;

import java.math.BigDecimal;

// Only the 5 fields explicitly required to be independently editable with history tracking -
// deliberately not the full CandidateRequest, so this endpoint can't be used to bypass the
// propose/approve workflow for offer-related fields.
public record CandidateFieldsUpdateRequest(
        BigDecimal expectedSalary,
        BigDecimal currentCtc,
        Integer noticePeriod,
        String currentLocation,
        String preferredLocation
) {
}
