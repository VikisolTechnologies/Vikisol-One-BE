package com.vikisol.one.auth.controller;

import com.vikisol.one.auth.dto.*;
import com.vikisol.one.auth.service.AuthService;
import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.security.service.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // Sets the access/refresh/CSRF cookies directly on the response (see CookieService) instead
    // of returning them in the JSON body - see Phase 2 auth overhaul.
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request,
                                                            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        AuthResponse response = authService.login(request, httpRequest, httpResponse);
        String message = response.mfaRequired() ? "Enter your verification code to continue" : "Login successful";
        return ResponseEntity.ok(new ApiResponse<>(true, message, response));
    }

    public record MfaVerifyRequest(@NotBlank String challengeToken, @NotBlank String code, boolean remember) {}

    // Second step of login when MFA is enabled for the account - see AuthService.login's MFA gate.
    @PostMapping("/mfa/verify")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyMfa(@Valid @RequestBody MfaVerifyRequest request,
                                                                HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        AuthResponse response = authService.verifyMfaAndCompleteLogin(request.challengeToken(), request.code(), httpRequest, httpResponse, request.remember());
        return ResponseEntity.ok(new ApiResponse<>(true, "Login successful", response));
    }

    // Redeems the refresh cookie for a fresh access token - called automatically by the frontend
    // on a 401, not something a user ever triggers directly. Doesn't require a valid access token
    // (that's the point - the access token is expected to already be expired here).
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        AuthResponse response = authService.refresh(httpRequest, httpResponse);
        return ResponseEntity.ok(new ApiResponse<>(true, "Session refreshed", response));
    }

    // Real server-side logout (previously didn't exist at all - the frontend just discarded its
    // localStorage token). Revokes the current session/refresh-token family and clears cookies.
    // Deliberately tolerant of missing/expired cookies (idempotent) so "logout" always succeeds
    // from the user's perspective even if their session had already expired server-side.
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        authService.logout(httpRequest, httpResponse);
        return ResponseEntity.ok(new ApiResponse<>(true, "Logged out", null));
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        authService.changePassword(request, principal);
        return ResponseEntity.ok(new ApiResponse<>(true, "Password changed successfully", null));
    }

    // Step 1 of Forgot Password: employee supplies only their official company email. Always
    // responds with the same generic success message regardless of whether a matching account
    // exists (see AuthService.forgotPassword) - never reveal which official emails are registered.
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(new ApiResponse<>(true,
                "If this official email is registered, a password reset link has been sent to the linked personal email on file.", null));
    }

    @GetMapping("/reset-password/{token}")
    public ResponseEntity<ApiResponse<ActivationTokenInfo>> inspectResetToken(@PathVariable String token) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Reset token checked", authService.inspectResetToken(token)));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Password updated successfully", null));
    }

    @GetMapping("/activate/{token}")
    public ResponseEntity<ApiResponse<ActivationTokenInfo>> inspectActivationToken(@PathVariable String token) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Activation token checked", authService.inspectActivationToken(token)));
    }

    @PostMapping("/activate")
    public ResponseEntity<ApiResponse<Void>> activateAccount(@Valid @RequestBody ActivateAccountRequest request) {
        authService.activateAccount(request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Account activated successfully", null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> me(
            @AuthenticationPrincipal UserPrincipal principal) {
        Map<String, Object> userInfo = Map.of(
                "id", principal.getId(),
                "email", principal.getEmail(),
                "firstName", principal.getFirstName(),
                "lastName", principal.getLastName(),
                "role", principal.getAuthorities().iterator().next().getAuthority()
        );
        return ResponseEntity.ok(new ApiResponse<>(true, "Current user", userInfo));
    }
}
