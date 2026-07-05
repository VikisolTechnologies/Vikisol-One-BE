package com.vikisol.one.integration.controller;

import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.integration.dto.IntegrationSettingsRequest;
import com.vikisol.one.integration.dto.IntegrationSettingsResponse;
import com.vikisol.one.integration.entity.IntegrationSettings;
import com.vikisol.one.integration.service.IntegrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Backs the "Company Integrations" admin page - CEO/Admin only, since this holds credentials
// (Azure AD client secret, future SMTP/Slack/Zoom/DocuSign tokens) that must not be readable or
// editable by any other role.
@RestController
@RequestMapping("/admin/integrations")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('CEO','ADMIN')")
public class IntegrationController {

    private final IntegrationService integrationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<IntegrationSettingsResponse>>> getAll() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Integrations retrieved", integrationService.getAll()));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<IntegrationSettingsResponse>> save(@RequestBody IntegrationSettingsRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Integration saved", integrationService.save(request)));
    }

    @PostMapping("/{type}/test")
    public ResponseEntity<ApiResponse<IntegrationSettingsResponse>> testConnection(@PathVariable IntegrationSettings.IntegrationType type) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Connection tested", integrationService.testConnection(type)));
    }
}
