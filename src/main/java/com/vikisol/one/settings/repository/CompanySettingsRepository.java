package com.vikisol.one.settings.repository;

import com.vikisol.one.settings.entity.CompanySettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanySettingsRepository extends JpaRepository<CompanySettings, UUID> {

    Optional<CompanySettings> findByKey(String key);

    List<CompanySettings> findByCategory(CompanySettings.SettingsCategory category);
}
