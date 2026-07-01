package com.vikisol.one.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        @NotBlank(message = "Old password is required") String oldPassword,
        @NotBlank(message = "New password is required") String newPassword
) {
}
