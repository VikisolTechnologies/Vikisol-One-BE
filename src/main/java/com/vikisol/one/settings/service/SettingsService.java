package com.vikisol.one.settings.service;

import com.vikisol.one.settings.dto.*;
import com.vikisol.one.settings.entity.CompanySettings;
import com.vikisol.one.settings.entity.Holiday;
import com.vikisol.one.settings.repository.CompanySettingsRepository;
import com.vikisol.one.settings.repository.HolidayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final CompanySettingsRepository settingsRepository;
    private final HolidayRepository holidayRepository;

    // Company Settings

    public List<CompanySettingsResponse> getAllSettings() {
        return settingsRepository.findAll().stream()
                .map(this::toSettingsResponse)
                .collect(Collectors.toList());
    }

    public List<CompanySettingsResponse> getSettingsByCategory(CompanySettings.SettingsCategory category) {
        return settingsRepository.findByCategory(category).stream()
                .map(this::toSettingsResponse)
                .collect(Collectors.toList());
    }

    public CompanySettingsResponse updateSetting(CompanySettingsRequest request) {
        CompanySettings settings = settingsRepository.findByKey(request.key())
                .orElse(CompanySettings.builder()
                        .key(request.key())
                        .category(request.category())
                        .build());

        settings.setValue(request.value());
        settings.setDescription(request.description());
        if (request.dataType() != null) {
            settings.setDataType(request.dataType());
        }
        if (request.category() != null) {
            settings.setCategory(request.category());
        }

        return toSettingsResponse(settingsRepository.save(settings));
    }

    // Holidays

    public List<HolidayResponse> getHolidaysForYear(int year) {
        return holidayRepository.findByYear(year).stream()
                .map(this::toHolidayResponse)
                .collect(Collectors.toList());
    }

    public HolidayResponse createHoliday(HolidayRequest request) {
        Holiday holiday = Holiday.builder()
                .name(request.name())
                .date(request.date())
                .type(request.type())
                .isOptional(request.isOptional())
                .year(request.date().getYear())
                .description(request.description())
                .build();

        return toHolidayResponse(holidayRepository.save(holiday));
    }

    public HolidayResponse updateHoliday(UUID id, HolidayRequest request) {
        Holiday holiday = holidayRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Holiday not found with id: " + id));

        holiday.setName(request.name());
        holiday.setDate(request.date());
        holiday.setType(request.type());
        holiday.setOptional(request.isOptional());
        holiday.setYear(request.date().getYear());
        holiday.setDescription(request.description());

        return toHolidayResponse(holidayRepository.save(holiday));
    }

    public void deleteHoliday(UUID id) {
        holidayRepository.deleteById(id);
    }

    public boolean isHoliday(LocalDate date) {
        return holidayRepository.findByDate(date).isPresent();
    }

    private CompanySettingsResponse toSettingsResponse(CompanySettings settings) {
        return new CompanySettingsResponse(
                settings.getId(), settings.getKey(), settings.getValue(),
                settings.getCategory(), settings.getDescription(), settings.getDataType(),
                settings.getCreatedAt(), settings.getUpdatedAt()
        );
    }

    private HolidayResponse toHolidayResponse(Holiday holiday) {
        return new HolidayResponse(
                holiday.getId(), holiday.getName(), holiday.getDate(),
                holiday.getType(), holiday.isOptional(), holiday.getYear(),
                holiday.getDescription(), holiday.getCreatedAt(), holiday.getUpdatedAt()
        );
    }
}
