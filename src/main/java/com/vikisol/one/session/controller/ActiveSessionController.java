package com.vikisol.one.session.controller;

import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.security.cookie.CookieService;
import com.vikisol.one.security.jwt.JwtTokenProvider;
import com.vikisol.one.security.service.UserPrincipal;
import com.vikisol.one.session.dto.ActiveSessionResponse;
import com.vikisol.one.session.service.ActiveSessionService;
import com.vikisol.one.session.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Active Sessions: list/revoke real per-device sessions tracked by JWT "jti" (see ActiveSession).
// Revoking a session also revokes the refresh-token family tied to it (see RefreshTokenService),
// so a revoked device can't just silently mint a new access token via /auth/refresh either.
@RestController
@RequiredArgsConstructor
public class ActiveSessionController {

    private final ActiveSessionService activeSessionService;
    private final RefreshTokenService refreshTokenService;
    private final JwtTokenProvider jwtTokenProvider;
    private final CookieService cookieService;

    private String currentJti(HttpServletRequest request) {
        String token = cookieService.readCookie(request, CookieService.ACCESS_COOKIE);
        if (token == null) return null;
        try {
            return jwtTokenProvider.getJtiFromToken(token);
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
        String revokedJti = activeSessionService.revoke(id, isAdmin ? null : principal.getEmail());
        if (revokedJti != null) refreshTokenService.revokeBySessionJti(revokedJti);
        return ResponseEntity.ok(new ApiResponse<>(true, "Session revoked", null));
    }

    @PostMapping("/sessions/revoke-all")
    public ResponseEntity<ApiResponse<Void>> revokeAllMine(@AuthenticationPrincipal UserPrincipal principal) {
        activeSessionService.revokeAllForUser(principal.getEmail());
        refreshTokenService.revokeAllForUser(principal.getEmail());
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

    // Force logout - admin revokes every session (and refresh token) for a specific user (e.g.
    // offboarded/compromised account).
    @PostMapping("/sessions/force-logout/{userEmail}")
    @PreAuthorize("hasAnyRole('CEO','ADMIN')")
    public ResponseEntity<ApiResponse<Void>> forceLogout(@PathVariable String userEmail) {
        activeSessionService.revokeAllForUser(userEmail);
        refreshTokenService.revokeAllForUser(userEmail);
        return ResponseEntity.ok(new ApiResponse<>(true, "User has been force-logged-out of all sessions", null));
    }
}
