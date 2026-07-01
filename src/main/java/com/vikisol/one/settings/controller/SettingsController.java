package com.vikisol.one.settings.controller;

import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.settings.dto.*;
import com.vikisol.one.settings.entity.CompanySettings;
import com.vikisol.one.settings.service.SettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping("/company")
    @PreAuthorize("hasAnyRole('ADMIN', 'CEO')")
    public ResponseEntity<ApiResponse<List<CompanySettingsResponse>>> getAllSettings() {
        List<CompanySettingsResponse> settings = settingsService.getAllSettings();
        return ResponseEntity.ok(new ApiResponse<>(true, "Settings retrieved successfully", settings));
    }

    @PutMapping("/company")
    @PreAuthorize("hasAnyRole('ADMIN', 'CEO')")
    public ResponseEntity<ApiResponse<CompanySettingsResponse>> updateSetting(
            @Valid @RequestBody CompanySettingsRequest request) {
        CompanySettingsResponse settings = settingsService.updateSetting(request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Setting updated successfully", settings));
    }

    @GetMapping("/company/{category}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CEO')")
    public ResponseEntity<ApiResponse<List<CompanySettingsResponse>>> getSettingsByCategory(
            @PathVariable CompanySettings.SettingsCategory category) {
        List<CompanySettingsResponse> settings = settingsService.getSettingsByCategory(category);
        return ResponseEntity.ok(new ApiResponse<>(true, "Settings retrieved successfully", settings));
    }

    @GetMapping("/holidays")
    public ResponseEntity<ApiResponse<List<HolidayResponse>>> getHolidays(@RequestParam int year) {
        List<HolidayResponse> holidays = settingsService.getHolidaysForYear(year);
        return ResponseEntity.ok(new ApiResponse<>(true, "Holidays retrieved successfully", holidays));
    }

    @PostMapping("/holidays")
    @PreAuthorize("hasAnyRole('HR_MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<HolidayResponse>> createHoliday(
            @Valid @RequestBody HolidayRequest request) {
        HolidayResponse holiday = settingsService.createHoliday(request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Holiday created successfully", holiday));
    }

    @PutMapping("/holidays/{id}")
    @PreAuthorize("hasAnyRole('HR_MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<HolidayResponse>> updateHoliday(
            @PathVariable UUID id,
            @Valid @RequestBody HolidayRequest request) {
        HolidayResponse holiday = settingsService.updateHoliday(id, request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Holiday updated successfully", holiday));
    }

    @DeleteMapping("/holidays/{id}")
    @PreAuthorize("hasAnyRole('HR_MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteHoliday(@PathVariable UUID id) {
        settingsService.deleteHoliday(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Holiday deleted successfully", null));
    }
}
