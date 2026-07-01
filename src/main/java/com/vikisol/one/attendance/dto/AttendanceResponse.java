package com.vikisol.one.attendance.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record AttendanceResponse(
        UUID id,
        String employeeName,
        String employeeId,
        LocalDate date,
        LocalTime checkInTime,
        LocalTime checkOutTime,
        String status,
        double workingHours,
        double overtimeHours,
        String source,
        boolean isRegularized
) {
}
