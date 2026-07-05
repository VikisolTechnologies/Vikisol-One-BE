package com.vikisol.one.recruitment.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record CandidateFieldChangeResponse(
        UUID id,
        String fieldName,
        String previousValue,
        String newValue,
        String modifiedByName,
        LocalDateTime modifiedAt
) {
}
