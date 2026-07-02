package com.vikisol.one.timesheet.dto;

import com.vikisol.one.timesheet.entity.TimesheetEntry;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record TimesheetEntryRequest(
        @NotNull UUID projectId,
        UUID taskId,
        @NotNull LocalDate date,
        // hours is optional when checkInTime/checkOutTime are supplied - the service computes it
        Double hours,
        String description,
        LocalTime checkInTime,
        LocalTime checkOutTime,
        String reason,
        TimesheetEntry.WorkLocation workLocation
) {}
