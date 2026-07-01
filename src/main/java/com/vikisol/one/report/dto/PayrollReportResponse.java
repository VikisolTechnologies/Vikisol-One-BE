package com.vikisol.one.report.dto;

import java.math.BigDecimal;
import java.util.Map;

public record PayrollReportResponse(
        int month,
        int year,
        BigDecimal totalGrossPay,
        BigDecimal totalNetPay,
        BigDecimal totalPF,
        BigDecimal totalESI,
        BigDecimal totalTDS,
        Map<String, BigDecimal> departmentWiseCost
) {}
