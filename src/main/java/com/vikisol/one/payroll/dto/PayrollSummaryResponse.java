package com.vikisol.one.payroll.dto;

import java.math.BigDecimal;

public record PayrollSummaryResponse(
        int month,
        int year,
        long totalEmployees,
        BigDecimal totalGrossEarnings,
        BigDecimal totalDeductions,
        BigDecimal totalNetSalary,
        BigDecimal totalPfContribution,
        BigDecimal totalEsiContribution,
        String status
) {
}
