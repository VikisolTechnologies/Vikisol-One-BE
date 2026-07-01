package com.vikisol.one.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RegularizationRequest(
        @NotNull UUID attendanceId,
        @NotBlank String requestedStatus,
        @NotBlank String reason
) {
}
