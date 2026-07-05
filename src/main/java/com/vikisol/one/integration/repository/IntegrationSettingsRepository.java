package com.vikisol.one.integration.repository;

import com.vikisol.one.integration.entity.IntegrationSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IntegrationSettingsRepository extends JpaRepository<IntegrationSettings, java.util.UUID> {
    Optional<IntegrationSettings> findByType(IntegrationSettings.IntegrationType type);
}
