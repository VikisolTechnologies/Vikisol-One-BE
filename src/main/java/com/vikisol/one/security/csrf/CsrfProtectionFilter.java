package com.vikisol.one.security.csrf;

import com.vikisol.one.security.cookie.CookieService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

// Double-submit-cookie CSRF defense. Cookies are now auto-attached by the browser to every
// request (see the move from Authorization-header to HttpOnly cookies), so a malicious
// cross-site page could otherwise trigger state-changing requests using the victim's own
// cookies. This is mitigated in two independent layers: (1) SameSite=Strict on the auth cookies
// themselves already blocks them from being sent on cross-site requests in modern browsers, and
// (2) this filter, as defense-in-depth for older/misconfigured browsers - every mutating request
// must echo the CSRF cookie's value back in an X-XSRF-TOKEN header, which a cross-site attacker
// cannot read (the cookie isn't HttpOnly, but the browser's same-origin policy stops a foreign
// page's JS from reading it).
@Component
@RequiredArgsConstructor
public class CsrfProtectionFilter extends OncePerRequestFilter {

    private final CookieService cookieService;

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    // Endpoints reachable before a session (and therefore the CSRF cookie) exists yet.
    private static final Set<String> EXEMPT_PATHS = Set.of(
            "/auth/login", "/auth/mfa/verify", "/auth/otp/request", "/auth/otp/verify",
            "/auth/activate", "/auth/forgot-password", "/auth/reset-password", "/assessments/webhook");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String method = request.getMethod();
        String path = request.getRequestURI().replaceFirst("^/api/v1", "");

        if (SAFE_METHODS.contains(method) || EXEMPT_PATHS.contains(path) || path.startsWith("/auth/reset-password/") || path.startsWith("/auth/activate/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String cookieValue = cookieService.readCookie(request, CookieService.CSRF_COOKIE);
        String headerValue = request.getHeader("X-XSRF-TOKEN");

        if (cookieValue == null || headerValue == null || !cookieValue.equals(headerValue)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"message\":\"CSRF validation failed - please refresh and try again.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
