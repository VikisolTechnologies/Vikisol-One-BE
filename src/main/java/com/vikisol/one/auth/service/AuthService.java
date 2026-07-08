package com.vikisol.one.auth.service;

import com.vikisol.one.audit.service.AuditService;
import com.vikisol.one.auth.dto.*;
import com.vikisol.one.auth.entity.ActivationToken;
import com.vikisol.one.auth.entity.LoginHistoryEntry;
import com.vikisol.one.auth.entity.PasswordHistoryEntry;
import com.vikisol.one.auth.entity.PasswordResetToken;
import com.vikisol.one.auth.entity.User;
import com.vikisol.one.auth.entity.LoginOtp;
import com.vikisol.one.auth.repository.ActivationTokenRepository;
import com.vikisol.one.auth.repository.LoginHistoryEntryRepository;
import com.vikisol.one.auth.repository.LoginOtpRepository;
import com.vikisol.one.auth.repository.PasswordHistoryEntryRepository;
import com.vikisol.one.auth.repository.PasswordResetTokenRepository;
import com.vikisol.one.auth.repository.UserRepository;
import com.vikisol.one.common.exception.BadRequestException;
import com.vikisol.one.common.service.EmailService;
import com.vikisol.one.employee.entity.Employee;
import com.vikisol.one.employee.repository.EmployeeRepository;
import com.vikisol.one.mfa.service.MfaService;
import com.vikisol.one.security.cookie.CookieService;
import com.vikisol.one.security.jwt.JwtTokenProvider;
import com.vikisol.one.security.service.UserPrincipal;
import com.vikisol.one.session.service.ActiveSessionService;
import com.vikisol.one.session.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
    private final LoginHistoryEntryRepository loginHistoryEntryRepository;
    private final ActiveSessionService activeSessionService;
    private final RefreshTokenService refreshTokenService;
    private final MfaService mfaService;
    private final CookieService cookieService;
    private final LoginOtpRepository loginOtpRepository;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    // Lockout thresholds live in LoginLockoutService now (persisted, not in-memory, so state
    // survives a restart and is visible on the employee's "Linked Accounts" panel - also correct
    // across multiple app instances, unlike the previous in-memory-map implementation).
    private static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(30);
    private static final String RESET_TOKEN_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Duration REMEMBER_REFRESH_TTL = Duration.ofDays(30);
    private static final Duration DEFAULT_REFRESH_TTL = Duration.ofHours(12);

    // Forgot-password had no throttle at all - unlimited requests for the same email would each
    // send a real email (spam/email-bombing vector) and hammer the mail provider. In-memory is
    // fine for a single-instance deployment; a shared cache would be needed behind a load balancer.
    private static final java.util.concurrent.ConcurrentHashMap<String, Instant> FORGOT_PASSWORD_LAST_REQUEST = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Duration FORGOT_PASSWORD_COOLDOWN = Duration.ofMinutes(2);

    // OTP Login: a 6-digit code. Validity and resend-cooldown are deliberately decoupled - the
    // code itself stays usable for 5 minutes (realistic for email delivery latency), while a
    // fresh one can be requested every 30 seconds regardless of whether the previous one has
    // expired yet (each new request invalidates the previous code, see requestOtp).
    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final Duration OTP_REQUEST_COOLDOWN = Duration.ofSeconds(30);
    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final java.util.concurrent.ConcurrentHashMap<String, Instant> OTP_LAST_REQUEST = new java.util.concurrent.ConcurrentHashMap<>();

    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
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
                // toMinutes() truncates towards zero, so any remaining time under 60 seconds
                // (which still legitimately fails the isAfter check above) rendered as the
                // nonsensical "try again after 0 minute(s)" right up until the lockout actually
                // expired. Round up instead, and use clearer wording for the sub-minute case.
                long secondsLeft = Duration.between(Instant.now(), user.getLockedUntil()).getSeconds();
                long minutesLeft = (secondsLeft + 59) / 60;
                String wait = minutesLeft <= 1 ? "less than a minute" : minutesLeft + " minutes";
                throw new BadRequestException("Too many failed login attempts. Please try again after " + wait + ".");
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
            loginHistoryService.record(email, LoginHistoryEntry.EventType.LOGIN_FAILED, false);
            if (user != null) {
                loginLockoutService.recordFailedAttempt(user);
            }
            throw e;
        }

        User authedUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("User not found"));

        // MFA gate: password was correct, but a second factor is required before any real session
        // is created. No cookies, no ActiveSession, no RefreshToken yet - just a short-lived
        // challenge token distinct in kind from a real access token (see JwtTokenProvider).
        if (mfaService.isEnabled(authedUser.getId())) {
            loginHistoryService.record(email, LoginHistoryEntry.EventType.MFA_CHALLENGE_ISSUED, true);
            return AuthResponse.mfaChallenge(jwtTokenProvider.generateMfaChallengeToken(email));
        }

        return completeLogin(authedUser, authSettings, httpRequest, httpResponse, request.remember());
    }

    @Transactional
    public AuthResponse verifyMfaAndCompleteLogin(String challengeToken, String code, HttpServletRequest httpRequest, HttpServletResponse httpResponse, boolean remember) {
        if (!jwtTokenProvider.validateMfaChallengeToken(challengeToken)) {
            throw new BadRequestException("This login attempt has expired. Please sign in again.");
        }
        String email = jwtTokenProvider.getEmailFromToken(challengeToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("This login attempt has expired. Please sign in again."));

        if (!mfaService.verifyLogin(user.getId(), code.trim())) {
            loginHistoryService.record(email, LoginHistoryEntry.EventType.MFA_VERIFY_FAILED, false);
            throw new BadRequestException("Invalid or expired code. Please try again.");
        }

        return completeLogin(user, authSettingsService.getSettings(), httpRequest, httpResponse, remember);
    }

    // OTP Login, step 1: emails a 6-digit code to the OFFICIAL address the employee typed (not
    // personal - this proves ownership of the account they're signing into). Always responds the
    // same way regardless of whether the email matches a real account, same "don't leak which
    // emails exist" reasoning as forgotPassword.
    @Transactional
    public void requestOtp(OtpRequestDto request) {
        String email = request.email().trim().toLowerCase();

        Instant lastRequest = OTP_LAST_REQUEST.get(email);
        if (lastRequest != null && lastRequest.plus(OTP_REQUEST_COOLDOWN).isAfter(Instant.now())) {
            return;
        }
        OTP_LAST_REQUEST.put(email, Instant.now());

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !user.isEnabled()) {
            log.info("OTP requested for an email with no active account: {}", email);
            return;
        }

        loginOtpRepository.invalidateAllForEmail(email);
        String code = generateOtpCode();
        loginOtpRepository.save(LoginOtp.builder()
                .email(email)
                .codeHash(hashOtp(code))
                .expiresAt(Instant.now().plus(OTP_TTL))
                .used(false)
                .attempts(0)
                .build());

        try {
            emailService.sendLoginOtpEmail(user.getEmail(), user.getFirstName(), code, (int) OTP_TTL.getSeconds());
        } catch (Exception e) {
            log.warn("Could not send login OTP to {}: {}", email, e.getMessage());
        }
    }

    // OTP Login, step 2: verifies the code and completes login exactly like a password sign-in
    // (same cookies/session/refresh-token/login-history/alert path) - the OTP itself IS the
    // credential here, so this deliberately doesn't also gate on MFA (email delivery is already
    // a second channel beyond "something you know").
    @Transactional
    public AuthResponse verifyOtpAndCompleteLogin(OtpVerifyRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String email = request.email().trim().toLowerCase();
        LoginOtp otp = loginOtpRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new BadRequestException("Invalid or expired code. Please request a new one."));

        if (otp.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("This code has expired. Please request a new one.");
        }
        if (otp.getAttempts() >= MAX_OTP_ATTEMPTS) {
            throw new BadRequestException("Too many incorrect attempts. Please request a new code.");
        }
        if (!hashOtp(request.code().trim()).equals(otp.getCodeHash())) {
            otp.setAttempts(otp.getAttempts() + 1);
            loginOtpRepository.save(otp);
            throw new BadRequestException("Incorrect code. Please try again.");
        }

        otp.setUsed(true);
        loginOtpRepository.save(otp);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("Invalid or expired code. Please request a new one."));
        return completeLogin(user, authSettingsService.getSettings(), httpRequest, httpResponse, request.remember());
    }

    private String generateOtpCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private String hashOtp(String code) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(code.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Could not hash OTP code", e);
        }
    }

    // Shared tail of both the plain-password and post-MFA login paths: issues the real access +
    // refresh cookies, creates the ActiveSession, enforces a concurrent-session cap if configured,
    // sends a new-device alert if warranted, and records the successful login everywhere the rest
    // of the app already expects it (audit log, login history).
    private AuthResponse completeLogin(User authedUser, com.vikisol.one.settings.dto.AuthSettingsDto authSettings,
                                        HttpServletRequest httpRequest, HttpServletResponse httpResponse, boolean remember) {
        String email = authedUser.getEmail();
        String ip = extractClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        Integer maxConcurrent = authSettings.maxConcurrentSessions();
        if (maxConcurrent != null && maxConcurrent > 0) {
            activeSessionService.enforceConcurrentLimit(email, maxConcurrent - 1);
        }

        Duration accessTtl = Duration.ofMinutes(Math.max(1, authSettings.sessionTimeoutMinutes()));
        String accessToken = jwtTokenProvider.generateAccessToken(email, accessTtl);
        String jti = jwtTokenProvider.getJtiFromToken(accessToken);

        activeSessionService.recordLogin(email, jti);

        Duration refreshTtl = remember ? REMEMBER_REFRESH_TTL : DEFAULT_REFRESH_TTL;
        var issued = refreshTokenService.issueForNewFamily(email, Instant.now().plus(refreshTtl), ip, userAgent, jti);

        String csrfToken = cookieService.generateCsrfToken();
        cookieService.setAll(httpResponse, accessToken, accessTtl, issued.rawValue(), refreshTtl, remember, csrfToken);

        authedUser.setLastLoginAt(Instant.now());
        userRepository.save(authedUser);
        auditService.record("Login Succeeded", email, null);

        Instant loginMoment = Instant.now();
        maybeSendLoginAlert(authedUser, ip, userAgent, authSettings, loginMoment);
        loginHistoryService.record(email, LoginHistoryEntry.EventType.LOGIN_SUCCESS, true);

        boolean passwordExpired = isPasswordExpired(authedUser, authSettings);
        return new AuthResponse(authedUser.getEmail(), authedUser.getRole().name(),
                authedUser.getFirstName(), authedUser.getLastName(), passwordExpired, false, null);
    }

    // Login Alerts: if we've never recorded a successful login from this exact (IP, User-Agent)
    // pair for this user before, treat it as a new device/location and email a heads-up to their
    // personal/recovery address (same reasoning as password-reset emails going to the personal
    // inbox: if the official mailbox itself is what's compromised, the alert still gets through).
    private void maybeSendLoginAlert(User user, String ip, String userAgent, com.vikisol.one.settings.dto.AuthSettingsDto authSettings, Instant asOf) {
        if (!authSettings.loginAlertsEnabled()) return;
        if (ip == null && userAgent == null) return;
        LocalDateTime cutoff = LocalDateTime.ofInstant(asOf, ZoneId.systemDefault());
        boolean seenBefore = loginHistoryEntryRepository.existsByUserEmailAndIpAddressAndUserAgentAndEventTypeAndSuccessTrueAndCreatedAtBefore(
                user.getEmail(), ip, userAgent, LoginHistoryEntry.EventType.LOGIN_SUCCESS, cutoff);
        if (seenBefore) return;

        Employee employee = employeeRepository.findByUserId(user.getId()).orElse(null);
        String personalEmail = employee != null ? employee.getPersonalEmail() : null;
        if (personalEmail == null || personalEmail.isBlank()) return;

        String device = com.vikisol.one.session.util.DeviceInfoParser.parse(userAgent);
        String whenText = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm").format(cutoff);
        try {
            emailService.sendNewLoginAlertEmail(personalEmail, user.getFirstName(), device, ip, whenText);
        } catch (Exception e) {
            // Never let an alert-email failure block the login itself.
            log.warn("Could not send new-device login alert to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    // POST /auth/refresh - redeems the refresh cookie for a fresh access token, rotating the
    // refresh token itself in the process (see RefreshTokenService.rotate for the reuse-detection
    // design). Deliberately does NOT require a valid access token (that's the whole point - the
    // access token is expected to be expired/near-expired when this is called).
    @Transactional
    public AuthResponse refresh(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String presented = cookieService.readCookie(httpRequest, CookieService.REFRESH_COOKIE);
        if (presented == null) {
            cookieService.clearAll(httpResponse);
            throw new BadCredentialsException("Session expired. Please sign in again.");
        }

        String ip = extractClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        var authSettings = authSettingsService.getSettings();
        Duration accessTtl = Duration.ofMinutes(Math.max(1, authSettings.sessionTimeoutMinutes()));

        var rotation = refreshTokenService.rotate(presented, Instant.now(), ip, userAgent);
        if (!rotation.success()) {
            cookieService.clearAll(httpResponse);
            if (rotation.reuseDetected() && rotation.userEmail() != null) {
                activeSessionService.revokeAllForUser(rotation.userEmail());
                refreshTokenService.revokeAllForUser(rotation.userEmail());
                loginHistoryService.record(rotation.userEmail(), LoginHistoryEntry.EventType.SESSION_REUSE_DETECTED, false);
                auditService.record("Refresh Token Reuse Detected", rotation.userEmail(), "All sessions revoked as a precaution");
            }
            throw new BadCredentialsException("Session expired. Please sign in again.");
        }

        String email = rotation.userEmail();
        String newAccessToken = jwtTokenProvider.generateAccessToken(email, accessTtl);
        String newJti = jwtTokenProvider.getJtiFromToken(newAccessToken);
        refreshTokenService.updateSessionJti(rotation.newToken(), newJti);

        User user = userRepository.findByEmail(email).orElseThrow(() -> new BadCredentialsException("Session expired. Please sign in again."));
        activeSessionService.recordLogin(email, newJti);

        boolean wasRemember = Duration.between(rotation.newToken().getIssuedAt(), rotation.newToken().getExpiresAt()).compareTo(Duration.ofDays(1)) > 0;
        Duration refreshRemaining = Duration.between(Instant.now(), rotation.newToken().getExpiresAt());
        if (refreshRemaining.isNegative()) refreshRemaining = Duration.ZERO;
        String csrfToken = cookieService.generateCsrfToken();
        cookieService.setAll(httpResponse, newAccessToken, accessTtl, rotation.rawValue(), refreshRemaining, wasRemember, csrfToken);

        boolean passwordExpired = isPasswordExpired(user, authSettings);
        return new AuthResponse(user.getEmail(), user.getRole().name(), user.getFirstName(), user.getLastName(), passwordExpired, false, null);
    }

    @Transactional
    public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String accessToken = cookieService.readCookie(httpRequest, CookieService.ACCESS_COOKIE);
        String refreshToken = cookieService.readCookie(httpRequest, CookieService.REFRESH_COOKIE);

        if (accessToken != null && jwtTokenProvider.validateToken(accessToken)) {
            try {
                String jti = jwtTokenProvider.getJtiFromToken(accessToken);
                activeSessionService.revokeByJti(jti);
                String email = jwtTokenProvider.getEmailFromToken(accessToken);
                loginHistoryService.record(email, LoginHistoryEntry.EventType.LOGOUT, true);
            } catch (Exception ignored) {
                // Token present but unparsable/expired - still fine, we're clearing cookies regardless.
            }
        }
        if (refreshToken != null) {
            refreshTokenService.revokeFamilyContaining(refreshToken);
        }
        cookieService.clearAll(httpResponse);
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
        revokeAllSessionsAndRefreshTokens(user.getEmail());
        auditService.record("Password Changed", user.getEmail(), null);
        emailService.sendPasswordChangedEmail(user.getEmail(), user.getFirstName(), Instant.now().toString());
    }

    // Step 1-4 of Forgot Password: employee identifies themselves by OFFICIAL email only; the
    // reset link is always emailed to the linked Employee record's PERSONAL email, never the
    // official mailbox. Always returns success-shaped output regardless of whether the email
    // matched a real account, to avoid leaking which official emails exist in the system.
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String officialEmail = request.officialEmail().trim().toLowerCase();

        // Silently no-op instead of throwing, so this still doesn't leak whether the email exists
        // (matches the rest of this method's "always success-shaped" behavior) while still
        // actually stopping the repeat send.
        Instant lastRequest = FORGOT_PASSWORD_LAST_REQUEST.get(officialEmail);
        if (lastRequest != null && lastRequest.plus(FORGOT_PASSWORD_COOLDOWN).isAfter(Instant.now())) {
            log.info("Forgot-password requested for {} again within the cooldown window - ignored", officialEmail);
            return;
        }
        FORGOT_PASSWORD_LAST_REQUEST.put(officialEmail, Instant.now());

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
        loginHistoryService.record(user.getEmail(), LoginHistoryEntry.EventType.PASSWORD_RESET_REQUESTED, true);
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
        revokeAllSessionsAndRefreshTokens(user.getEmail());

        auditService.record("Password Reset Completed", user.getEmail(), null);
        loginHistoryService.record(user.getEmail(), LoginHistoryEntry.EventType.PASSWORD_RESET_COMPLETED, true);
        emailService.sendPasswordChangedEmail(user.getEmail(), user.getFirstName(), Instant.now().toString());
    }

    // Session Rotation: any password change/reset is a real, immediate "log out everywhere" - not
    // just the lazy passwordChangedAt-vs-token-issuedAt check on the next request, which only
    // catches stale ACCESS tokens. Without this, a leaked refresh token would keep silently
    // minting fresh access tokens even after the legitimate owner changed their password.
    private void revokeAllSessionsAndRefreshTokens(String email) {
        activeSessionService.revokeAllForUser(email);
        refreshTokenService.revokeAllForUser(email);
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
        loginHistoryService.record(user.getEmail(), LoginHistoryEntry.EventType.ACCOUNT_ACTIVATED, true);
        emailService.sendWelcomeEmail(user.getEmail(), user.getFirstName());
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
