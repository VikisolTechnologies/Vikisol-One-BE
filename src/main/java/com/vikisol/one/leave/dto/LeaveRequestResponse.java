package com.vikisol.one.leave.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record LeaveRequestResponse(
        UUID id,
        String employeeName,
        String employeeId,
        String leaveType,
        LocalDate startDate,
        LocalDate endDate,
        double numberOfDays,
        String reason,
        String status,
        String approverName,
        String approverComments,
        LocalDateTime appliedOn,
        boolean isHalfDay
) {
}
