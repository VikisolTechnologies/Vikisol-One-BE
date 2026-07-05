package com.vikisol.one.employee.dto;

import com.vikisol.one.employee.entity.EmployeeTransfer;

import java.time.LocalDate;

// newValue is the identifier the caller means for the given transferType: a Department id (UUID
// string) for DEPARTMENT, an Employee id (UUID string) for REPORTING_MANAGER, or a plain string
// for LOCATION/COST_CENTER/BUSINESS_UNIT.
public record TransferRequest(
        EmployeeTransfer.TransferType transferType,
        String newValue,
        LocalDate effectiveDate,
        String reason
) {}
