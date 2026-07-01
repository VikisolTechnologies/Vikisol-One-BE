package com.vikisol.one.payroll.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record SalaryAdvanceRequest(
        @NotNull @Positive BigDecimal amount,
        String reason,
        @NotNull @Positive Integer emiMonths
) {
}
