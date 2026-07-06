package com.vikisol.one.auth.dto;

import jakarta.validation.constraints.NotBlank;

// The employee only ever supplies their official company email here - never the personal one.
public record ForgotPasswordRequest(@NotBlank String officialEmail) {
}
