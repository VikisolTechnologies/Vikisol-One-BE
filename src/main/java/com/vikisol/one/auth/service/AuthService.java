package com.vikisol.one.auth.service;

import com.vikisol.one.audit.service.AuditService;
import com.vikisol.one.auth.dto.*;
import com.vikisol.one.auth.entity.ActivationToken;
import com.vikisol.one.auth.entity.PasswordHistoryEntry;
import com.vikisol.one.auth.entity.PasswordResetToken;
import com.vikisol.one.auth.entity.User;
import com.vikisol.one.auth.repository.ActivationTokenRepository;
import com.vikisol.one.auth.repository.PasswordHistoryEntryRepository;
import com.vikisol.one.auth.repository.PasswordResetTokenRepository;
import com.vikisol.one.auth.repository.UserRepository;
import com.vikisol.one.common.exception.BadRequestException;
import com.vikisol.one.common.service.EmailService;
import com.vikisol.one.employee.entity.Employee;
import com.vikisol.one.employee.repository.EmployeeRepository;
import com.vikisol.one.security.jwt.JwtTokenProvider;
import com.vikisol.one.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final ActivationTokenRepository activationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;
    private final AuditService auditService;
    private final LoginLockoutService loginLockoutService;
    private final com.vikisol.one.settings.service.AuthSettingsService authSettingsService;
    private final PasswordPolicy passwordPolicy;
    private final PasswordHistoryEntryRepository passwordHistoryEntryRepository;
    private final LoginHistoryService loginHistoryService;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    // Lockout thresholds live in LoginLockoutService now (persisted, not in-memory, so state
    // survives a restart and is visible on the employee's "Linked Accounts" panel - also correct
    // across multiple app instances, unlike the previous in-memory-map implementation).
    private static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(30);
    private static final String RESET_TOKEN_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional
    public AuthResponse login(LoginRequest request) {
        // Safety guard: never actually honor "email+password disabled" while Microsoft login isn't
        // configured (no real Azure app credentials exist in this deployment yet) - that combination
        // would lock every single employee out of the system with no working alternative. Only
        // respect the toggle once Microsoft sign-in is genuinely usable.
        var authSettings = authSettingsService.getSettings();
        if (!authSettings.emailPasswordLoginEnabled() && authSettings.microsoftLoginConfigured() && authSettings.microsoftLoginEnabled()) {
            throw new BadRequestException("Email/password login is currently disabled. Please use Sign in with Microsoft.");
        }

        String email = request.email().trim().toLowerCase();
        User user = userRepository.findByEmail(email).orElse(null);

        if (user != null && user.getLockedUntil() != null) {
            if (user.getLockedUntil().isAfter(Instant.now())) {
                throw new BadRequestException("Too many failed login attempts. Please try again after "
                        + Duration.between(Instant.now(), user.getLockedUntil()).toMinutes() + " minute(s).");
            }
            // Lockout window has passed - clear it so this attempt is evaluated normally.
            user.setLockedUntil(null);
            user.setFailedLoginCount(0);
            userRepository.save(user);
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password())
            );
        } catch (BadCredentialsException e) {
            loginHistoryService.record(email, com.vikisol.one.auth.entity.LoginHistoryEntry.EventType.LOGIN_FAILED, false);
            if (user != null) {
                loginLockoutService.recordFailedAttempt(user);
            }
            throw e;
        }

        String token = jwtTokenProvider.generateToken(authentication);
        String refreshToken = jwtTokenProvider.generateTokenFromEmail(email);

        User authedUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("User not found"));
        authedUser.setLastLoginAt(Instant.now());
        userRepository.save(authedUser);
        auditService.record("Login Succeeded", authedUser.getEmail(), null);
        loginHistoryService.record(email, com.vikisol.one.auth.entity.LoginHistoryEntry.EventType.LOGIN_SUCCESS, true);

        boolean passwordExpired = isPasswordExpired(authedUser, authSettings);
        return new AuthResponse(token, refreshToken, authedUser.getEmail(),
                authedUser.getRole().name(), authedUser.getFirstName(), authedUser.getLastName(), passwordExpired);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request, UserPrincipal principal) {
        User user = userRepository.findByEmail(principal.getEmail())
                .orElseThrow(() -> new BadRequestException("User not found"));

        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            throw new BadRequestException("Old password is incorrect");
        }
        passwordPolicy.assertValid(request.newPassword());
        assertPasswordNotRecentlyUsed(user, request.newPassword());

        String newHash = passwordEncoder.encode(request.newPassword());
        user.setPassword(newHash);
        user.setPasswordChangedAt(Instant.now());
        userRepository.save(user);
        recordPasswordHistory(user, newHash);
        passwordResetTokenRepository.invalidateAllForUser(user);
        auditService.record("Password Changed", user.getEmail(), null);
    }

    // Step 1-4 of Forgot Password: employee identifies themselves by OFFICIAL email only; the
    // reset link is always emailed to the linked Employee record's PERSONAL email, never the
    // official mailbox. Always returns success-shaped output regardless of whether the email
    // matched a real account, to avoid leaking which official emails exist in the system.
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String officialEmail = request.officialEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(officialEmail).orElse(null);
        if (user == null) {
            log.info("Forgot-password requested for an email with no matching account: {}", officialEmail);
            return;
        }
        Employee employee = employeeRepository.findByUserId(user.getId()).orElse(null);
        String personalEmail = employee != null ? employee.getPersonalEmail() : null;
        if (personalEmail == null || personalEmail.isBlank()) {
            log.warn("Forgot-password requested for {} but no personal/recovery email is on file - cannot send a reset link", officialEmail);
            return;
        }

        String token = generateResetToken();
        passwordResetTokenRepository.save(PasswordResetToken.builder()
                .token(token)
                .user(user)
                .expiresAt(Instant.now().plus(RESET_TOKEN_TTL))
                .used(false)
                .build());

        String resetLink = frontendUrl + "/reset-password?token=" + token;
        emailService.sendPasswordResetEmail(personalEmail, user.getFirstName(), user.getEmail(), resetLink);
        auditService.record("Password Reset Requested", user.getEmail(), "Reset link sent to linked personal email");
        loginHistoryService.record(user.getEmail(), com.vikisol.one.auth.entity.LoginHistoryEntry.EventType.PASSWORD_RESET_REQUESTED, true);
    }

    @Transactional(readOnly = true)
    public ActivationTokenInfo inspectResetToken(String token) {
        return passwordResetTokenRepository.findByToken(token)
                .filter(t -> !t.isUsed() && t.getExpiresAt().isAfter(Instant.now()))
                .map(t -> new ActivationTokenInfo(true, t.getUser().getFirstName(), t.getUser().getEmail()))
                .orElseGet(() -> new ActivationTokenInfo(false, null, null));
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(request.token())
                .orElseThrow(() -> new BadRequestException("This password reset link is invalid"));
        if (resetToken.isUsed()) {
            throw new BadRequestException("This password reset link has already been used");
        }
        if (resetToken.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("This password reset link has expired. Please request a new one.");
        }
        User user = resetToken.getUser();
        passwordPolicy.assertValid(request.newPassword());
        assertPasswordNotRecentlyUsed(user, request.newPassword());

        String newHash = passwordEncoder.encode(request.newPassword());
        user.setPassword(newHash);
        user.setPasswordChangedAt(Instant.now());
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        recordPasswordHistory(user, newHash);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);
        // Any other still-outstanding reset link for this user (e.g. requested twice) is now stale.
        passwordResetTokenRepository.invalidateAllForUser(user);

        auditService.record("Password Reset Completed", user.getEmail(), null);
        loginHistoryService.record(user.getEmail(), com.vikisol.one.auth.entity.LoginHistoryEntry.EventType.PASSWORD_RESET_COMPLETED, true);
    }

    // Password History: blocks reuse of the last N passwords (N = AuthSettingsDto.passwordHistoryCount(),
    // CEO/Admin-configurable, 0 = disabled). Checks the CURRENT password too, since it wouldn't be
    // in the history table yet at the moment of a same-password "change".
    private void assertPasswordNotRecentlyUsed(User user, String rawNewPassword) {
        int historyCount = authSettingsService.getSettings().passwordHistoryCount();
        if (historyCount <= 0) return;
        if (passwordEncoder.matches(rawNewPassword, user.getPassword())) {
            throw new BadRequestException("You cannot reuse your current password. Please choose a new one.");
        }
        List<PasswordHistoryEntry> recent = passwordHistoryEntryRepository
                .findByUserOrderByCreatedAtDesc(user, PageRequest.of(0, historyCount));
        for (PasswordHistoryEntry entry : recent) {
            if (passwordEncoder.matches(rawNewPassword, entry.getPasswordHash())) {
                throw new BadRequestException("You cannot reuse one of your last " + historyCount + " passwords. Please choose a new one.");
            }
        }
    }

    // Password Expiry: if enabled (a non-null day count in Authentication Settings), any user
    // whose passwordChangedAt is older than that many days is flagged so the frontend can force a
    // "Change Password" screen before continuing. Users who've never changed their password
    // (passwordChangedAt null - shouldn't normally happen once activation sets it, but defensively
    // handled) are NOT force-expired here, since there's no baseline to measure age from.
    private boolean isPasswordExpired(User user, com.vikisol.one.settings.dto.AuthSettingsDto settings) {
        Integer expiryDays = settings.passwordExpiryDays();
        if (expiryDays == null || expiryDays <= 0 || user.getPasswordChangedAt() == null) return false;
        return user.getPasswordChangedAt().plus(Duration.ofDays(expiryDays)).isBefore(Instant.now());
    }

    private void recordPasswordHistory(User user, String newHashedPassword) {
        passwordHistoryEntryRepository.save(PasswordHistoryEntry.builder()
                .user(user)
                .passwordHash(newHashedPassword)
                .build());
    }

    private String generateResetToken() {
        StringBuilder sb = new StringBuilder(40);
        for (int i = 0; i < 40; i++) {
            sb.append(RESET_TOKEN_CHARS.charAt(RANDOM.nextInt(RESET_TOKEN_CHARS.length())));
        }
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public ActivationTokenInfo inspectActivationToken(String token) {
        return activationTokenRepository.findByToken(token)
                .filter(t -> !t.isUsed() && t.getExpiresAt().isAfter(Instant.now()))
                .map(t -> new ActivationTokenInfo(true, t.getUser().getFirstName(), t.getUser().getEmail()))
                .orElseGet(() -> new ActivationTokenInfo(false, null, null));
    }

    @Transactional
    public void activateAccount(ActivateAccountRequest request) {
        ActivationToken activationToken = activationTokenRepository.findByToken(request.token())
                .orElseThrow(() -> new BadRequestException("This activation link is invalid"));
        if (activationToken.isUsed()) {
            throw new BadRequestException("This activation link has already been used");
        }
        if (activationToken.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("This activation link has expired. Please ask HR to resend it.");
        }
        User user = activationToken.getUser();
        passwordPolicy.assertValid(request.password());

        String newHash = passwordEncoder.encode(request.password());
        user.setPassword(newHash);
        user.setPasswordChangedAt(Instant.now());
        user.setEnabled(true);
        userRepository.save(user);
        recordPasswordHistory(user, newHash);

        activationToken.setUsed(true);
        activationTokenRepository.save(activationToken);
        auditService.record("Account Activated", user.getEmail(), null);
        loginHistoryService.record(user.getEmail(), com.vikisol.one.auth.entity.LoginHistoryEntry.EventType.ACCOUNT_ACTIVATED, true);
    }
}
