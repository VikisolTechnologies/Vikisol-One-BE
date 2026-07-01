package com.vikisol.one.payroll.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SalaryAdvanceResponse(
        UUID id,
        String employeeName,
        BigDecimal amount,
        LocalDate requestDate,
        String status,
        int emiMonths,
        BigDecimal emiAmount,
        BigDecimal remainingAmount
) {
}
