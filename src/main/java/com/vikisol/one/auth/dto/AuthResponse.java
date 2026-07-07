package com.vikisol.one.auth.dto;

public record AuthResponse(
        // No token/refreshToken fields anymore - both are now set as HttpOnly cookies the
        // frontend never touches directly (see Phase 2 auth overhaul). email/role/name are still
        // useful to the frontend as an immediate first paint before the /auth/me round trip.
        String email,
        String role,
        String firstName,
        String lastName,
        // True when Company Settings' Password Expiry is enabled and this user's password is
        // older than that many days. The cookies are still issued (so the frontend can call
        // /auth/change-password), but the frontend must force a "Change Password" screen and
        // block normal navigation until it succeeds.
        boolean passwordExpired,
        // When true, no session cookies were issued yet - the password was correct but this
        // account has MFA enabled. The frontend must show a 6-digit code entry screen and POST it
        // to /auth/mfa/verify along with challengeToken to actually complete login.
        boolean mfaRequired,
        String challengeToken
) {
    public static AuthResponse mfaChallenge(String challengeToken) {
        return new AuthResponse(null, null, null, null, false, true, challengeToken);
    }
}
