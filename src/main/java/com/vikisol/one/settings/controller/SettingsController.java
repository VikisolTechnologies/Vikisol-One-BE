package com.vikisol.one.settings.controller;

import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.security.RoleEnum;
import com.vikisol.one.security.service.UserPrincipal;
import com.vikisol.one.settings.dto.*;
import com.vikisol.one.settings.entity.CompanySettings;
import com.vikisol.one.settings.service.RolePermissionService;
import com.vikisol.one.settings.service.SettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;
    private final RolePermissionService rolePermissionService;

    // ── Role Permissions (CEO controls what each role can see) ───────────────

    @GetMapping("/role-permissions")
    @PreAuthorize("hasRole('CEO')")
    public ResponseEntity<ApiResponse<List<RolePermissionEntry>>> getRolePermissionMatrix() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Role permission matrix retrieved", rolePermissionService.getMatrix()));
    }

    @PutMapping("/role-permissions")
    @PreAuthorize("hasRole('CEO')")
    public ResponseEntity<ApiResponse<List<RolePermissionEntry>>> updateRolePermissionMatrix(
            @RequestBody List<RolePermissionEntry> updates) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Role permissions updated", rolePermissionService.updateMatrix(updates)));
    }

    // Any authenticated user can fetch which modules their own role can see, to drive the nav.
    @GetMapping("/role-permissions/me")
    public ResponseEntity<ApiResponse<Set<String>>> getMyVisibleModules(@AuthenticationPrincipal UserPrincipal principal) {
        RoleEnum role = principal.getAuthorities().stream()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .map(RoleEnum::valueOf)
                .findFirst()
                .orElse(RoleEnum.EMPLOYEE);
        return ResponseEntity.ok(new ApiResponse<>(true, "Visible modules retrieved", rolePermissionService.getVisibleModules(role)));
    }

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
