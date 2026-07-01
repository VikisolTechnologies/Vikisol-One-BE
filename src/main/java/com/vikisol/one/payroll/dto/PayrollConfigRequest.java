package com.vikisol.one.payroll.dto;

import jakarta.validation.constraints.NotBlank;

public record PayrollConfigRequest(
        @NotBlank String key,
        @NotBlank String value,
        String description,
        String category
) {
}
