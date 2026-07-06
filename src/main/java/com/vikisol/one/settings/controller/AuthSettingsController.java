package com.vikisol.one.settings.controller;

import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.settings.dto.AuthSettingsDto;
import com.vikisol.one.settings.service.AuthSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// GET must stay public (see SecurityConfig) - the Login page needs to know, before the employee
// is authenticated, whether to show "Sign in with Microsoft" at all.
@RestController
@RequestMapping("/auth-settings")
@RequiredArgsConstructor
public class AuthSettingsController {

    private final AuthSettingsService authSettingsService;

    @GetMapping
    public ResponseEntity<ApiResponse<AuthSettingsDto>> getSettings() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Authentication settings retrieved", authSettingsService.getSettings()));
    }

    // Keys: EMAIL_PASSWORD_ENABLED, MICROSOFT_ENABLED, LOCKOUT_ENABLED, MAX_FAILED_ATTEMPTS,
    // LOCKOUT_MINUTES, PASSWORD_EXPIRY_DAYS, SESSION_TIMEOUT_MINUTES, PASSWORD_MIN_LENGTH,
    // PASSWORD_REQUIRE_UPPERCASE, PASSWORD_REQUIRE_LOWERCASE, PASSWORD_REQUIRE_NUMBER,
    // PASSWORD_REQUIRE_SPECIAL_CHAR, PASSWORD_HISTORY_COUNT
    @PutMapping
    @PreAuthorize("hasAnyRole('CEO','ADMIN')")
    public ResponseEntity<ApiResponse<AuthSettingsDto>> updateSettings(@RequestBody Map<String, String> fields) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Authentication settings updated", authSettingsService.updateSettings(fields)));
    }
}
