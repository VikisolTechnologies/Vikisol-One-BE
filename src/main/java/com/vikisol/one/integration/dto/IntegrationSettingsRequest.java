package com.vikisol.one.integration.dto;

import com.vikisol.one.integration.entity.IntegrationSettings;

import java.util.Map;

public record IntegrationSettingsRequest(
        IntegrationSettings.IntegrationType type,
        boolean enabled,
        // Provider-specific fields, e.g. Microsoft 365: {tenantId, clientId, clientSecret}.
        // Any key whose value is exactly "UNCHANGED" is left as-is (used so the frontend never
        // has to re-submit a secret it can't see after masking).
        Map<String, String> config
) {
}
