package com.vikisol.one.leave.dto;

import java.util.UUID;

public record LeaveTypeResponse(
        UUID id,
        String name,
        String code,
        int defaultDays,
        boolean carryForward,
        int maxCarryForwardDays,
        boolean isActive
) {
}
