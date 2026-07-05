package com.vikisol.one.auth.controller;

import com.vikisol.one.auth.dto.*;
import com.vikisol.one.auth.service.AuthService;
import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.security.service.UserPrincipal;
import jakarta.validation.Valid;
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

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Login successful", response));
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        authService.changePassword(request, principal);
        return ResponseEntity.ok(new ApiResponse<>(true, "Password changed successfully", null));
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
