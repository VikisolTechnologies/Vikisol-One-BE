package com.vikisol.one.employee.dto;

import com.vikisol.one.employee.entity.BackgroundCheck;

import java.time.LocalDateTime;
import java.util.UUID;

public record BackgroundCheckResponse(
        UUID id,
        BackgroundCheck.CheckType checkType,
        BackgroundCheck.Status status,
        String remarks,
        String reviewedByName,
        LocalDateTime reviewedAt
) {
}
