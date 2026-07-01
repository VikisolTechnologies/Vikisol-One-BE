package com.vikisol.one.auth.dto;

import com.vikisol.one.security.RoleEnum;
import jakarta.validation.constraints.NotBlank;

public record RegisterRequest(
        @NotBlank(message = "Email is required") String email,
        @NotBlank(message = "Password is required") String password,
        @NotBlank(message = "First name is required") String firstName,
        @NotBlank(message = "Last name is required") String lastName,
        RoleEnum role
) {
    public RegisterRequest {
        if (role == null) {
            role = RoleEnum.EMPLOYEE;
        }
    }
}
