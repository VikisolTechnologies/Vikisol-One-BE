package com.vikisol.one.settings.dto;

import com.vikisol.one.settings.entity.CompanySettings;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CompanySettingsRequest(
        @NotBlank String key,
        String value,
        @NotNull CompanySettings.SettingsCategory category,
        String description,
        CompanySettings.DataType dataType
) {}
