package com.vikisol.one.attendance.dto;

import java.time.LocalDate;
import java.util.UUID;

public record AttendanceReportRequest(
        UUID employeeId,
        LocalDate startDate,
        LocalDate endDate
) {
}
