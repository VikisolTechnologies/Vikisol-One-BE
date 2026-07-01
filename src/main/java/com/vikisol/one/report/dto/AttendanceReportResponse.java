package com.vikisol.one.report.dto;

public record AttendanceReportResponse(
        String employeeName,
        String employeeId,
        long presentDays,
        long absentDays,
        long halfDays,
        long leaveDays,
        double avgWorkingHours,
        int month,
        int year
) {}
