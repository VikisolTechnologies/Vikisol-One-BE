package com.vikisol.one.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Email is required") String email,
        @NotBlank(message = "Password is required") String password,
        // Controls refresh-token cookie lifetime: true = persistent 30-day cookie, false = a real
        // browser session cookie (gone when the browser closes), backstopped by a 12h server-side
        // absolute expiry either way. Defaults to false (record components default only via the
        // canonical constructor; Jackson supplies false for a missing boolean field, which is the
        // safe default here).
        boolean remember
) {
}
