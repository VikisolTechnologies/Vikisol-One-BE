package com.vikisol.one.offboarding.dto;

import com.vikisol.one.offboarding.entity.OffboardingCase;

import java.time.LocalDate;
import java.util.UUID;

// Lightweight row for the case-list table - full checklist/history is only fetched on
// case-detail view via OffboardingCaseResponse.
public record OffboardingCaseSummary(
        UUID id,
        UUID employeeId,
        String employeeCode,
        String employeeName,
        String department,
        OffboardingCase.Type type,
        OffboardingCase.Stage stage,
        OffboardingCase.CaseStatus status,
        LocalDate lastWorkingDate,
        int daysInStage,
        int checklistCompleted,
        int checklistTotal
) {}
