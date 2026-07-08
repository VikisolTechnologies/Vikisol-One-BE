package com.vikisol.one.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record OtpVerifyRequest(
        @NotBlank(message = "Email is required") String email,
        @NotBlank(message = "Code is required") String code,
        boolean remember
) {
}
