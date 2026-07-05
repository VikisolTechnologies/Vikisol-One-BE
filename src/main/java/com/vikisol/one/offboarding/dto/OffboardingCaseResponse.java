package com.vikisol.one.offboarding.dto;

import com.vikisol.one.offboarding.entity.OffboardingCase;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record OffboardingCaseResponse(
        UUID id,
        UUID employeeId,
        String employeeCode,
        String employeeName,
        String department,
        UUID reportingManagerId,
        LocalDate initiatedDate,
        LocalDate lastWorkingDate,
        String reason,
        OffboardingCase.Type type,
        OffboardingCase.Stage stage,
        OffboardingCase.CaseStatus status,
        boolean bgvRequired,
        int daysInStage,
        boolean itClearanceEligible,
        List<ChecklistItemResponse> checklist,
        List<StageHistoryResponse> history
) {}
