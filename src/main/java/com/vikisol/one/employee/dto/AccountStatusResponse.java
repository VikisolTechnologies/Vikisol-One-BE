package com.vikisol.one.employee.dto;

import java.time.Instant;

// Backs the Employee Profile's "Linked Accounts" panel - a consolidated view of an employee's
// identity/security state so HR/CEO can troubleshoot login issues without digging through the
// database directly.
public record AccountStatusResponse(
        String officialEmail,
        String personalEmail,
        String accountStatus,      // "ACTIVE" | "LOCKED" | "PENDING_ACTIVATION" | "DISABLED"
        Instant lastLoginAt,
        Instant passwordChangedAt,
        Instant lockedUntil,
        Integer failedLoginCount,
        // Microsoft 365 sign-in is not implemented in this system (see MicrosoftAuthController) -
        // these are honest placeholders reflecting that fact, not a working integration.
        boolean microsoftLoginAvailable,
        boolean microsoftLinked,
        Instant lastMicrosoftAuthAt
) {}
