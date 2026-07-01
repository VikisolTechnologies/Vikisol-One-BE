package com.vikisol.one.attendance.dto;

public record MonthlyAttendanceSummary(
        int totalDays,
        int presentDays,
        int absentDays,
        int halfDays,
        int leaveDays,
        int holidays,
        int weekends,
        double avgWorkingHours,
        double totalOvertimeHours
) {
}
