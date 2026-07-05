package com.vikisol.one.offboarding.dto;

import com.vikisol.one.offboarding.entity.OffboardingChecklistItem;

public record ChecklistUpdateRequest(
        OffboardingChecklistItem.Status status,
        String remarks
) {}
