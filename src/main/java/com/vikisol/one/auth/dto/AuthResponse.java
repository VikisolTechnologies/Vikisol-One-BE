package com.vikisol.one.auth.dto;

public record AuthResponse(
        String token,
        String refreshToken,
        String email,
        String role,
        String firstName,
        String lastName,
        // True when Company Settings' Password Expiry is enabled and this user's password is
        // older than that many days. The token is still issued (so the frontend can call
        // /auth/change-password), but the frontend must force a "Change Password" screen and
        // block normal navigation until it succeeds.
        boolean passwordExpired
) {
}
