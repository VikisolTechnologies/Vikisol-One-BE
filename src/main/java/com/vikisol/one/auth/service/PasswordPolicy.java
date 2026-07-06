package com.vikisol.one.auth.service;

import com.vikisol.one.common.exception.BadRequestException;
import com.vikisol.one.settings.dto.AuthSettingsDto;
import com.vikisol.one.settings.service.AuthSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

// Shared complexity rule for every "employee sets/resets their own password" moment (account
// activation, forgot-password reset) - kept in one place so the two flows can't drift apart on
// what "strong enough" means. Rules are CEO/Admin-configurable via Authentication Settings
// (AuthSettingsService), not hardcoded - see updateSettings for the PASSWORD_* keys.
// Deliberately NOT applied to ChangePasswordRequest in this pass (out of scope for the auth
// rebuild that introduced this), which still only enforces min-length.
@Component
@RequiredArgsConstructor
public class PasswordPolicy {

    private final AuthSettingsService authSettingsService;

    private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL = Pattern.compile("[^A-Za-z0-9]");

    public void assertValid(String password) {
        AuthSettingsDto settings = authSettingsService.getSettings();
        if (password == null || password.length() < settings.passwordMinLength()) {
            throw new BadRequestException("Password must be at least " + settings.passwordMinLength() + " characters long");
        }
        if (settings.passwordRequireUppercase() && !UPPERCASE.matcher(password).find()) {
            throw new BadRequestException("Password must contain at least one uppercase letter");
        }
        if (settings.passwordRequireLowercase() && !LOWERCASE.matcher(password).find()) {
            throw new BadRequestException("Password must contain at least one lowercase letter");
        }
        if (settings.passwordRequireNumber() && !DIGIT.matcher(password).find()) {
            throw new BadRequestException("Password must contain at least one number");
        }
        if (settings.passwordRequireSpecialChar() && !SPECIAL.matcher(password).find()) {
            throw new BadRequestException("Password must contain at least one special character");
        }
    }
}
