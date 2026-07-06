package com.vikisol.one.settings.dto;

public record AuthSettingsDto(
        boolean emailPasswordLoginEnabled,
        boolean microsoftLoginEnabled,
        // Real, computed check - true only if Microsoft/Azure AD app credentials are actually
        // configured via environment variables (they are not, in this deployment). The CEO can
        // still flip microsoftLoginEnabled on, but the login page must only show the "Sign in
        // with Microsoft" button when BOTH this is true AND the toggle is on - see
        // MicrosoftAuthController's fallback-disable behavior.
        boolean microsoftLoginConfigured,
        boolean googleLoginEnabled,       // future - always false today, no Google OAuth built
        boolean mfaEnabled,               // future - always false today, no MFA built
        boolean accountLockoutEnabled,
        int maxFailedLoginAttempts,
        int lockoutDurationMinutes,
        Integer passwordExpiryDays,       // null = no expiry enforced
        int sessionTimeoutMinutes,
        int passwordMinLength,
        boolean passwordRequireUppercase,
        boolean passwordRequireLowercase,
        boolean passwordRequireNumber,
        boolean passwordRequireSpecialChar,
        int passwordHistoryCount          // 0 = reuse of old passwords not restricted
) {}
