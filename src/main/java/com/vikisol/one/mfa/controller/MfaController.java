package com.vikisol.one.mfa.controller;

import com.vikisol.one.auth.repository.UserRepository;
import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.common.exception.BadRequestException;
import com.vikisol.one.mfa.dto.MfaDtos.*;
import com.vikisol.one.mfa.service.MfaService;
import com.vikisol.one.security.service.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

// Self-service MFA enrollment - every authenticated user may enroll regardless of role
// (AuthSettingsDto.mfaNudgedRoles only controls who gets NUDGED to, in the Security Dashboard).
@RestController
@RequestMapping("/auth/mfa")
@RequiredArgsConstructor
public class MfaController {

    private final MfaService mfaService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<StatusResponse>> status(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(new ApiResponse<>(true, "MFA status", new StatusResponse(mfaService.isEnabled(principal.getId()))));
    }

    @PostMapping("/setup")
    public ResponseEntity<ApiResponse<SetupResponse>> setup(@AuthenticationPrincipal UserPrincipal principal) {
        var result = mfaService.startSetup(principal.getId(), principal.getEmail());
        return ResponseEntity.ok(new ApiResponse<>(true, "Scan this QR code with your authenticator app", new SetupResponse(result.qrCodeDataUri(), result.manualEntryKey())));
    }

    @PostMapping("/enable")
    public ResponseEntity<ApiResponse<EnableResponse>> enable(@Valid @RequestBody VerifyCodeRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        var backupCodes = mfaService.enable(principal.getId(), request.code());
        return ResponseEntity.ok(new ApiResponse<>(true, "MFA enabled - save your backup codes somewhere safe, they won't be shown again", new EnableResponse(backupCodes)));
    }

    @PostMapping("/disable")
    public ResponseEntity<ApiResponse<Void>> disable(@Valid @RequestBody DisableRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        var user = userRepository.findByEmail(principal.getEmail()).orElseThrow(() -> new BadRequestException("User not found"));
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BadRequestException("Incorrect password");
        }
        mfaService.disable(principal.getId());
        return ResponseEntity.ok(new ApiResponse<>(true, "MFA disabled", null));
    }
}
