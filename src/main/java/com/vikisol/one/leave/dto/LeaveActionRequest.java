package com.vikisol.one.leave.dto;

import jakarta.validation.constraints.NotBlank;

public record LeaveActionRequest(
        @NotBlank String action,
        String comments
) {
}
