package com.vikisol.one.leave.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LeaveTypeRequest(
        @NotBlank String name,
        @NotBlank String code,
        @NotNull Integer defaultDays,
        boolean carryForward,
        int maxCarryForwardDays
) {
}
