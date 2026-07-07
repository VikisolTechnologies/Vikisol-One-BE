package com.vikisol.one.security.jwt;

import com.vikisol.one.security.cookie.CookieService;
import com.vikisol.one.security.service.CustomUserDetailsService;
import com.vikisol.one.settings.service.AuthSettingsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final AuthSettingsService authSettingsService;
    private final com.vikisol.one.session.service.ActiveSessionService activeSessionService;
    private final CookieService cookieService;

    // Requests a user with an expired password must still be able to make - everything else is
    // blocked with 403 until they change it. "/auth/login" isn't here since it's permitAll and
    // never reaches this authenticated branch anyway.
    private static final Set<String> ALLOWED_WHEN_PASSWORD_EXPIRED = Set.of(
            "/auth/change-password", "/auth/me");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = getTokenFromRequest(request);

        if (StringUtils.hasText(token) && jwtTokenProvider.validateAccessToken(token)) {
            String email = jwtTokenProvider.getEmailFromToken(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            // Session invalidation: reject any token issued before the user's password last
            // changed, even though its signature and expiry are otherwise still valid. This is
            // what makes "signed out of all devices after a password reset" actually true for a
            // stateless JWT - every existing token everywhere becomes invalid the instant
            // passwordChangedAt moves forward, with no server-side session/blacklist store needed.
            com.vikisol.one.security.service.UserPrincipal principal =
                    (com.vikisol.one.security.service.UserPrincipal) userDetails;
            Instant passwordChangedAt = principal.getPasswordChangedAt();
            Instant tokenIssuedAt = jwtTokenProvider.getIssuedAtFromToken(token);
            boolean staleSession = passwordChangedAt != null && tokenIssuedAt.isBefore(passwordChangedAt);

            // Per-session revocation (Active Sessions "Revoke"/"Force Logout") - independent of
            // the passwordChangedAt check above. touch() returns false only if this specific jti
            // was explicitly revoked; it returns true (fail-open) for tokens issued before this
            // feature existed, so pre-existing sessions aren't retroactively logged out.
            boolean sessionRevoked = !activeSessionService.touch(jwtTokenProvider.getJtiFromToken(token));

            if (!staleSession && !sessionRevoked) {
                // Password Expiry enforcement: a real server-side block, not just a frontend
                // redirect the employee could bypass by calling the API directly. Every endpoint
                // except change-password/me returns 403 until the password is updated.
                Integer expiryDays = authSettingsService.getSettings().passwordExpiryDays();
                boolean expired = expiryDays != null && expiryDays > 0 && passwordChangedAt != null
                        && passwordChangedAt.plus(Duration.ofDays(expiryDays)).isBefore(Instant.now());

                if (expired && !ALLOWED_WHEN_PASSWORD_EXPIRED.contains(request.getRequestURI().replaceFirst("^/api/v1", ""))) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"success\":false,\"message\":\"Your password has expired. Please change it to continue.\"}");
                    return;
                }

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }

    // Reads the access token from the __Host-access_token HttpOnly cookie - the Authorization
    // header is no longer used at all (see the Phase 2 auth overhaul: cookies instead of
    // localStorage, to remove the token from any surface an XSS payload could read).
    private String getTokenFromRequest(HttpServletRequest request) {
        return cookieService.readCookie(request, CookieService.ACCESS_COOKIE);
    }
}
