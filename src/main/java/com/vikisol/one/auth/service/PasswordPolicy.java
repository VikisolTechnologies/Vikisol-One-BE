package com.vikisol.one.auth.service;

import com.vikisol.one.common.exception.BadRequestException;

import java.util.regex.Pattern;

// Shared complexity rule for every "employee sets/resets their own password" moment (account
// activation, forgot-password reset) - kept in one place so the two flows can't drift apart on
// what "strong enough" means. Deliberately NOT applied to ChangePasswordRequest in this pass
// (out of scope for the auth rebuild that introduced this), which still only enforces min-length.
public final class PasswordPolicy {

    private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL = Pattern.compile("[^A-Za-z0-9]");

    private PasswordPolicy() {
    }

    public static void assertValid(String password) {
        if (password == null || password.length() < 8) {
            throw new BadRequestException("Password must be at least 8 characters long");
        }
        if (!UPPERCASE.matcher(password).find()) {
            throw new BadRequestException("Password must contain at least one uppercase letter");
        }
        if (!LOWERCASE.matcher(password).find()) {
            throw new BadRequestException("Password must contain at least one lowercase letter");
        }
        if (!DIGIT.matcher(password).find()) {
            throw new BadRequestException("Password must contain at least one number");
        }
        if (!SPECIAL.matcher(password).find()) {
            throw new BadRequestException("Password must contain at least one special character");
        }
    }
}
