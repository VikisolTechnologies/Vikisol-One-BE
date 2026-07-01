package com.vikisol.one.timesheet.dto;

import java.time.LocalDate;
import java.util.UUID;

public record TimesheetEntryResponse(
        UUID id, UUID employeeId, String employeeName,
        UUID projectId, String projectName,
        UUID taskId, String taskTitle,
        LocalDate date, Double hours, String description,
        String status, UUID approvedById
) {}
