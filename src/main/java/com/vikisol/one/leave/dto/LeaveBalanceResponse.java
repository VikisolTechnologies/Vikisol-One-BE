package com.vikisol.one.leave.dto;

public record LeaveBalanceResponse(
        String leaveType,
        double totalDays,
        double usedDays,
        double remainingDays,
        double carryForwardDays,
        int year
) {
}
