package com.vikisol.one.settings.dto;

import com.vikisol.one.settings.entity.CompanySettings;

import java.time.LocalDateTime;
import java.util.UUID;

public record CompanySettingsResponse(
        UUID id,
        String key,
        String value,
        CompanySettings.SettingsCategory category,
        String description,
        CompanySettings.DataType dataType,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
