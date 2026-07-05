package com.vikisol.one.integration.dto;

import com.vikisol.one.integration.entity.IntegrationSettings;

import java.time.LocalDateTime;
import java.util.Map;

public record IntegrationSettingsResponse(
        IntegrationSettings.IntegrationType type,
        boolean enabled,
        IntegrationSettings.ConnectionStatus status,
        LocalDateTime lastTestedAt,
        String lastError,
        // Secret-looking keys (secret/password/token/key) are masked to "••••••••" - the raw value
        // never round-trips back to the browser once saved.
        Map<String, String> config
) {
}
