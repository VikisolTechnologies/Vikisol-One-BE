package com.vikisol.one.session.controller;

import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.security.jwt.JwtTokenProvider;
import com.vikisol.one.security.service.UserPrincipal;
import com.vikisol.one.session.dto.ActiveSessionResponse;
import com.vikisol.one.session.service.ActiveSessionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Active Sessions: list/revoke real per-device sessions tracked by JWT "jti" (see ActiveSession).
// Revoking one session only rejects that specific token going forward - it does NOT touch the
// user's other sessions, unlike the password-change-triggered "log out everywhere" path.
@RestController
@RequiredArgsConstructor
public class ActiveSessionController {

    private final ActiveSessionService activeSessionService;
    private final JwtTokenProvider jwtTokenProvider;

    private String currentJti(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        try {
            return jwtTokenProvider.getJtiFromToken(header.substring(7));
        } catch (Exception e) {
            return null;
        }
    }

    @GetMapping("/sessions/me")
    public ResponseEntity<ApiResponse<List<ActiveSessionResponse>>> mySessions(
            @AuthenticationPrincipal UserPrincipal principal, HttpServletRequest request) {
        String jti = currentJti(request);
        List<ActiveSessionResponse> sessions = activeSessionService.listForUser(principal.getEmail()).stream()
                .map(s -> ActiveSessionResponse.from(s, jti)).toList();
        return ResponseEntity.ok(new ApiResponse<>(true, "Active sessions retrieved", sessions));
    }

    // Ownership-checked: a non-admin user may only revoke their OWN sessions, never another
    // user's, by guessing/incrementing a session id.
    @PostMapping("/sessions/{id}/revoke")
    public ResponseEntity<ApiResponse<Void>> revokeMine(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CEO") || a.getAuthority().equals("ROLE_ADMIN"));
        activeSessionService.revoke(id, isAdmin ? null : principal.getEmail());
        return ResponseEntity.ok(new ApiResponse<>(true, "Session revoked", null));
    }

    @PostMapping("/sessions/revoke-all")
    public ResponseEntity<ApiResponse<Void>> revokeAllMine(@AuthenticationPrincipal UserPrincipal principal) {
        activeSessionService.revokeAllForUser(principal.getEmail());
        return ResponseEntity.ok(new ApiResponse<>(true, "All sessions revoked - you will need to sign in again on other devices", null));
    }

    @GetMapping("/sessions")
    @PreAuthorize("hasAnyRole('CEO','ADMIN')")
    public ResponseEntity<ApiResponse<List<ActiveSessionResponse>>> allSessions(HttpServletRequest request) {
        String jti = currentJti(request);
        List<ActiveSessionResponse> sessions = activeSessionService.listAll().stream()
                .map(s -> ActiveSessionResponse.from(s, jti)).toList();
        return ResponseEntity.ok(new ApiResponse<>(true, "All active sessions retrieved", sessions));
    }

    // Force logout - admin revokes every session for a specific user (e.g. offboarded/compromised account).
    @PostMapping("/sessions/force-logout/{userEmail}")
    @PreAuthorize("hasAnyRole('CEO','ADMIN')")
    public ResponseEntity<ApiResponse<Void>> forceLogout(@PathVariable String userEmail) {
        activeSessionService.revokeAllForUser(userEmail);
        return ResponseEntity.ok(new ApiResponse<>(true, "User has been force-logged-out of all sessions", null));
    }
}
