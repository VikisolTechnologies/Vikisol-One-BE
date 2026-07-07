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
        // Org-wide MFA feature flag - whether MFA enrollment is offered at all. Per-user
        // enforcement is separate (MfaSecret.enabled) - this doesn't force anyone to enroll, it
        // just hides/shows the enrollment UI. See mfaNudgedRoles for who gets nudged to enroll.
        boolean mfaEnabled,
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
        int passwordHistoryCount,         // 0 = reuse of old passwords not restricted
        boolean loginAlertsEnabled,       // email the employee when a login succeeds from a device/IP never seen before
        Integer maxConcurrentSessions,    // null = unlimited; oldest session is revoked when exceeded
        String mfaNudgedRoles             // comma-separated RoleEnum names shown a "please enable MFA" prompt
) {}
