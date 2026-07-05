package com.vikisol.one.employee.dto;

import com.vikisol.one.employee.entity.EmployeeTransfer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransferResponse(
        UUID id,
        UUID employeeId,
        EmployeeTransfer.TransferType transferType,
        String previousValue,
        String newValue,
        LocalDate effectiveDate,
        String reason,
        UUID initiatedById,
        String initiatedByName,
        LocalDateTime createdAt
) {}
