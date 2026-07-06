package com.vikisol.one.policy.dto;

import java.time.LocalDateTime;
import java.util.UUID;

// One row of the HR-facing compliance report: an employee's ack status for a given policy.
public record PolicyAcknowledgementRecord(
        UUID employeeId,
        String employeeName,
        String employeeCode,
        String department,
        String status,
        LocalDateTime viewedAt,
        LocalDateTime acceptedAt
) {
}
