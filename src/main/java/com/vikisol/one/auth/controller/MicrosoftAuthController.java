package com.vikisol.one.auth.controller;

import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.common.exception.BadRequestException;
import com.vikisol.one.settings.service.AuthSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Deliberately NOT a working Microsoft/Azure AD OAuth2 integration - this system's GoDaddy-managed
// Microsoft 365 tenant has no Azure app registration (client ID/secret/tenant ID) configured
// anywhere in this deployment, and building a real "Sign in with Microsoft" flow requires the
// company to create one in their Azure AD tenant and provide those credentials as environment
// variables (microsoft.oauth.client-id / microsoft.oauth.tenant-id / microsoft.oauth.client-secret).
// Until that happens, AuthSettingsService.isMicrosoftLoginConfigured() is honestly false, the
// frontend hides/disables the button accordingly, and this endpoint exists only so a direct call
// fails with a clear, correct error instead of a 404 or (worse) a fake success.
@RestController
@RequestMapping("/auth/microsoft")
@RequiredArgsConstructor
public class MicrosoftAuthController {

    private final AuthSettingsService authSettingsService;

    @GetMapping("/authorize-url")
    public ResponseEntity<ApiResponse<Void>> getAuthorizeUrl() {
        if (!authSettingsService.isMicrosoftLoginConfigured()) {
            throw new BadRequestException(
                    "Microsoft sign-in is not configured for this tenant. An Azure AD app registration " +
                    "(client ID, tenant ID, client secret) must be provided by an administrator before this feature can be enabled.");
        }
        // Not implemented - reaching here would require real Azure AD credentials to exist, which
        // they do not in any environment today.
        throw new BadRequestException("Microsoft sign-in integration is not yet implemented.");
    }
}
