package com.vikisol.one.offboarding.dto;

import com.vikisol.one.offboarding.entity.OffboardingChecklistItem;

import java.time.LocalDateTime;
import java.util.UUID;

public record ChecklistItemResponse(
        UUID id,
        OffboardingChecklistItem.Category category,
        String label,
        OffboardingChecklistItem.Status status,
        UUID completedById,
        String completedByName,
        LocalDateTime completedAt,
        String remarks
) {}
