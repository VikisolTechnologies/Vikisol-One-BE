package com.vikisol.one.leave.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record LeaveApplyRequest(
        @NotNull UUID leaveTypeId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        String reason,
        boolean isHalfDay,
        String halfDayType
) {
}
