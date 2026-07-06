package com.vikisol.one.settings.service;

import com.vikisol.one.audit.service.AuditService;
import com.vikisol.one.settings.dto.AuthSettingsDto;
import com.vikisol.one.settings.entity.CompanySettings;
import com.vikisol.one.settings.repository.CompanySettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

// Backs the CEO/Admin-only "Authentication" settings page. Same pattern as BrandingService -
// stored as CompanySettings rows (category AUTH) so these change at runtime with no redeploy.
@Service
@RequiredArgsConstructor
public class AuthSettingsService {

    private final CompanySettingsRepository settingsRepository;
    private final AuditService auditService;

    // Real, not a toggle - true only if an actual Microsoft/Azure AD app registration's
    // credentials are present in the environment. Nobody has provided these for the GoDaddy
    // Microsoft 365 tenant yet, so this is false in every environment today. Do not fake this.
    @Value("${microsoft.oauth.client-id:}")
    private String microsoftClientId;
    @Value("${microsoft.oauth.tenant-id:}")
    private String microsoftTenantId;

    private static final Map<String, String> DEFAULTS = Map.ofEntries(
            Map.entry("AUTH_EMAIL_PASSWORD_ENABLED", "true"),
            Map.entry("AUTH_MICROSOFT_ENABLED", "false"),
            Map.entry("AUTH_LOCKOUT_ENABLED", "true"),
            Map.entry("AUTH_MAX_FAILED_ATTEMPTS", "5"),
            Map.entry("AUTH_LOCKOUT_MINUTES", "15"),
            Map.entry("AUTH_PASSWORD_EXPIRY_DAYS", ""),
            Map.entry("AUTH_SESSION_TIMEOUT_MINUTES", "1440")
    );

    public boolean isMicrosoftLoginConfigured() {
        return microsoftClientId != null && !microsoftClientId.isBlank()
                && microsoftTenantId != null && !microsoftTenantId.isBlank();
    }

    public AuthSettingsDto getSettings() {
        boolean lockoutEnabled = Boolean.parseBoolean(get("AUTH_LOCKOUT_ENABLED", DEFAULTS.get("AUTH_LOCKOUT_ENABLED")));
        boolean microsoftEnabled = Boolean.parseBoolean(get("AUTH_MICROSOFT_ENABLED", DEFAULTS.get("AUTH_MICROSOFT_ENABLED")));
        String expiryDays = get("AUTH_PASSWORD_EXPIRY_DAYS", DEFAULTS.get("AUTH_PASSWORD_EXPIRY_DAYS"));
        return new AuthSettingsDto(
                Boolean.parseBoolean(get("AUTH_EMAIL_PASSWORD_ENABLED", DEFAULTS.get("AUTH_EMAIL_PASSWORD_ENABLED"))),
                microsoftEnabled,
                isMicrosoftLoginConfigured(),
                false,
                false,
                lockoutEnabled,
                Integer.parseInt(get("AUTH_MAX_FAILED_ATTEMPTS", DEFAULTS.get("AUTH_MAX_FAILED_ATTEMPTS"))),
                Integer.parseInt(get("AUTH_LOCKOUT_MINUTES", DEFAULTS.get("AUTH_LOCKOUT_MINUTES"))),
                (expiryDays == null || expiryDays.isBlank()) ? null : Integer.valueOf(expiryDays),
                Integer.parseInt(get("AUTH_SESSION_TIMEOUT_MINUTES", DEFAULTS.get("AUTH_SESSION_TIMEOUT_MINUTES")))
        );
    }

    public AuthSettingsDto updateSettings(Map<String, String> fields) {
        fields.forEach((key, value) -> {
            String settingKey = "AUTH_" + key;
            CompanySettings settings = settingsRepository.findByKey(settingKey)
                    .orElse(CompanySettings.builder()
                            .key(settingKey)
                            .category(CompanySettings.SettingsCategory.AUTH)
                            .build());
            settings.setValue(value);
            settings.setDescription("Authentication setting: " + key);
            settingsRepository.save(settings);
        });
        auditService.record("Authentication Settings Updated", "Authentication", "Fields changed: " + fields.keySet());
        return getSettings();
    }

    private String get(String key, String fallback) {
        return settingsRepository.findByKey(key).map(CompanySettings::getValue).filter(v -> !v.isBlank()).orElse(fallback);
    }
}
