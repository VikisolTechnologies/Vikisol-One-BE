package com.vikisol.one.integration.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vikisol.one.integration.dto.IntegrationSettingsRequest;
import com.vikisol.one.integration.dto.IntegrationSettingsResponse;
import com.vikisol.one.integration.entity.IntegrationSettings;
import com.vikisol.one.integration.provider.*;
import com.vikisol.one.integration.provider.microsoft.Microsoft365Provider;
import com.vikisol.one.integration.repository.IntegrationSettingsRepository;
import com.vikisol.one.integration.util.CryptoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

// Single place that (a) stores/retrieves per-provider config (encrypted at rest) and (b) resolves
// which concrete provider implementation the rest of the app should use right now. Recruitment/
// Onboarding/etc. should only ever depend on the CalendarProvider/MeetingProvider/MailProvider
// interfaces and call through here to get an instance - never construct Microsoft365Provider (or
// a future GoogleWorkspaceProvider) directly, or the "swap providers without touching
// Recruitment" goal breaks immediately.
@Service
@RequiredArgsConstructor
public class IntegrationService {

    private final IntegrationSettingsRepository repository;
    private final CryptoUtil cryptoUtil;
    private final NoopMeetingProvider noopMeetingProvider;
    private final NoopMailProvider noopMailProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Set<String> SECRET_KEYS = Set.of("clientsecret", "password", "token", "apikey", "secret");

    @Transactional(readOnly = true)
    public List<IntegrationSettingsResponse> getAll() {
        Map<IntegrationSettings.IntegrationType, IntegrationSettings> existing = new HashMap<>();
        repository.findAll().forEach(s -> existing.put(s.getType(), s));

        return Arrays.stream(IntegrationSettings.IntegrationType.values())
                .map(type -> existing.containsKey(type) ? toResponse(existing.get(type)) : emptyResponse(type))
                .toList();
    }

    @Transactional
    public IntegrationSettingsResponse save(IntegrationSettingsRequest request) {
        IntegrationSettings settings = repository.findByType(request.type())
                .orElse(IntegrationSettings.builder().type(request.type()).build());

        Map<String, String> existingConfig = decodeConfig(settings.getConfigJson());
        Map<String, String> merged = new HashMap<>(existingConfig);
        if (request.config() != null) {
            request.config().forEach((k, v) -> {
                if (!"UNCHANGED".equals(v)) merged.put(k, v);
            });
        }

        settings.setEnabled(request.enabled());
        settings.setConfigJson(encodeConfig(merged));
        // Any config edit invalidates the last test result - force "Test Connection" again
        // before this provider is trusted, rather than showing a stale CONNECTED status.
        settings.setStatus(IntegrationSettings.ConnectionStatus.NOT_CONFIGURED);
        settings.setLastError(null);
        return toResponse(repository.save(settings));
    }

    @Transactional
    public IntegrationSettingsResponse testConnection(IntegrationSettings.IntegrationType type) {
        IntegrationSettings settings = repository.findByType(type)
                .orElseThrow(() -> new IllegalStateException("This integration has not been configured yet"));
        Map<String, String> config = decodeConfig(settings.getConfigJson());

        try {
            if (type == IntegrationSettings.IntegrationType.MICROSOFT_365) {
                Microsoft365Provider provider = buildMicrosoft365Provider(config);
                if (!provider.isConfigured()) throw new IllegalStateException("Tenant ID, Client ID, and Client Secret are all required");
                provider.testConnection();
            } else {
                throw new UnsupportedOperationException("Test connection is not yet implemented for " + type);
            }
            settings.setStatus(IntegrationSettings.ConnectionStatus.CONNECTED);
            settings.setLastError(null);
        } catch (Exception e) {
            settings.setStatus(IntegrationSettings.ConnectionStatus.ERROR);
            settings.setLastError(e.getMessage());
        }
        settings.setLastTestedAt(LocalDateTime.now());
        return toResponse(repository.save(settings));
    }

    // ─── Provider resolution ───

    @Transactional(readOnly = true)
    public MeetingProvider getMeetingProvider() {
        Microsoft365Provider m365 = tryBuildActiveMicrosoft365();
        return m365 != null ? m365 : noopMeetingProvider;
    }

    @Transactional(readOnly = true)
    public MailProvider getMailProvider() {
        Microsoft365Provider m365 = tryBuildActiveMicrosoft365();
        return m365 != null ? m365 : noopMailProvider;
    }

    private Microsoft365Provider tryBuildActiveMicrosoft365() {
        return repository.findByType(IntegrationSettings.IntegrationType.MICROSOFT_365)
                .filter(IntegrationSettings::isEnabled)
                .map(s -> buildMicrosoft365Provider(decodeConfig(s.getConfigJson())))
                .filter(Microsoft365Provider::isConfigured)
                .orElse(null);
    }

    private Microsoft365Provider buildMicrosoft365Provider(Map<String, String> config) {
        return new Microsoft365Provider(config.get("tenantId"), config.get("clientId"), config.get("clientSecret"));
    }

    // ─── Encoding/masking helpers ───

    private String encodeConfig(Map<String, String> config) {
        try {
            return cryptoUtil.encrypt(objectMapper.writeValueAsString(config));
        } catch (Exception e) {
            throw new IllegalStateException("Could not encode integration config", e);
        }
    }

    private Map<String, String> decodeConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) return new HashMap<>();
        try {
            return objectMapper.readValue(cryptoUtil.decrypt(configJson), new TypeReference<>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private Map<String, String> maskSecrets(Map<String, String> config) {
        Map<String, String> masked = new HashMap<>();
        config.forEach((k, v) -> {
            boolean isSecret = SECRET_KEYS.stream().anyMatch(k.toLowerCase()::contains);
            masked.put(k, isSecret && v != null && !v.isBlank() ? "••••••••" : v);
        });
        return masked;
    }

    private IntegrationSettingsResponse toResponse(IntegrationSettings s) {
        return new IntegrationSettingsResponse(s.getType(), s.isEnabled(), s.getStatus(), s.getLastTestedAt(), s.getLastError(), maskSecrets(decodeConfig(s.getConfigJson())));
    }

    private IntegrationSettingsResponse emptyResponse(IntegrationSettings.IntegrationType type) {
        return new IntegrationSettingsResponse(type, false, IntegrationSettings.ConnectionStatus.NOT_CONFIGURED, null, null, Map.of());
    }
}
