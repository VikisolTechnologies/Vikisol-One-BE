package com.vikisol.one.offboarding.dto;

import com.vikisol.one.offboarding.entity.OffboardingCase;

import java.time.LocalDateTime;
import java.util.UUID;

public record StageHistoryResponse(
        UUID id,
        OffboardingCase.Stage fromStage,
        OffboardingCase.Stage toStage,
        UUID changedById,
        String changedByName,
        LocalDateTime changedAt,
        String comments
) {}
