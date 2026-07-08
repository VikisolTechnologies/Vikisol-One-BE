package com.vikisol.one.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record OtpRequestDto(@NotBlank(message = "Email is required") String email) {
}
