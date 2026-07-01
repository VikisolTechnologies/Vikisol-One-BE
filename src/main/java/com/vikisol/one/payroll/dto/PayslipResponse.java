package com.vikisol.one.payroll.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PayslipResponse(
        UUID id,
        UUID employeeId,
        String employeeName,
        String employeeCode,
        String departmentName,
        int month,
        int year,
        // Earnings
        BigDecimal basicSalary,
        BigDecimal hra,
        BigDecimal conveyanceAllowance,
        BigDecimal medicalAllowance,
        BigDecimal specialAllowance,
        BigDecimal otherEarnings,
        BigDecimal grossEarnings,
        // Deductions
        BigDecimal pfEmployee,
        BigDecimal esiEmployee,
        BigDecimal professionalTax,
        BigDecimal tds,
        BigDecimal lopDeduction,
        BigDecimal otherDeductions,
        BigDecimal totalDeductions,
        // Net
        BigDecimal netSalary,
        // LOP
        int lopDays,
        int workingDays,
        int presentDays,
        int paidDays,
        // Employer
        BigDecimal pfEmployer,
        BigDecimal esiEmployer,
        // Status
        String status,
        LocalDateTime processedDate,
        UUID approvedById,
        LocalDateTime paidDate,
        String transactionReference
) {
}
