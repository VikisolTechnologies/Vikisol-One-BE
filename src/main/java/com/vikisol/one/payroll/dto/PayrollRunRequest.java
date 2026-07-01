package com.vikisol.one.payroll.dto;

import jakarta.validation.constraints.NotNull;

public record PayrollRunRequest(
        @NotNull Integer month,
        @NotNull Integer year
) {
}
