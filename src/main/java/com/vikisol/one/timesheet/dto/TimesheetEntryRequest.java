package com.vikisol.one.timesheet.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record TimesheetEntryRequest(
        @NotNull UUID projectId,
        UUID taskId,
        @NotNull LocalDate date,
        @NotNull Double hours,
        String description
) {}
