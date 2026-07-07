package com.vikisol.one.security.cookie;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

// Builds/clears every auth-related cookie. The access and refresh token cookies use the
// "__Host-" prefix - the strongest cookie-security primitive browsers offer: it forces Secure,
// forbids a Domain attribute (so the cookie can never be sent to another host even if one were
// set by mistake), and forces Path=/. This only works because the frontend proxies /api/** to
// the backend (see vercel.json), making every request genuinely same-origin from the browser's
// perspective - no cross-site SameSite=None cookie ever needs to exist.
@Component
public class CookieService {

    public static final String ACCESS_COOKIE = "__Host-access_token";
    public static final String REFRESH_COOKIE = "__Host-refresh_token";
    // Deliberately NOT HttpOnly and NOT "__Host-" prefixed - this one must be readable by the
    // frontend's own JS so it can echo the value back as the X-XSRF-TOKEN header (double-submit
    // CSRF defense, see CsrfProtectionFilter). Its own security property isn't secrecy - it's that
    // a cross-site attacker's forged request can't read this cookie's value to put in the header.
    public static final String CSRF_COOKIE = "XSRF-TOKEN";

    private static final SecureRandom RANDOM = new SecureRandom();

    public ResponseCookie buildAccessCookie(String token, Duration ttl) {
        return ResponseCookie.from(ACCESS_COOKIE, token)
                .httpOnly(true).secure(true).sameSite("Strict").path("/").maxAge(ttl).build();
    }

    public ResponseCookie buildRefreshCookie(String token, Duration ttl, boolean persistent) {
        var builder = ResponseCookie.from(REFRESH_COOKIE, token)
                .httpOnly(true).secure(true).sameSite("Strict").path("/");
        // persistent=false -> a real browser session cookie (no Max-Age at all), gone when the
        // browser closes; the server-side expiresAt on the RefreshToken row is the real backstop
        // either way, so a lost/misapplied Max-Age never grants a longer session than intended.
        if (persistent) builder.maxAge(ttl);
        return builder.build();
    }

    public String generateCsrfToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public ResponseCookie buildCsrfCookie(String value, Duration ttl) {
        return ResponseCookie.from(CSRF_COOKIE, value)
                .httpOnly(false).secure(true).sameSite("Strict").path("/").maxAge(ttl).build();
    }

    public ResponseCookie clearCookie(String name) {
        return ResponseCookie.from(name, "").httpOnly(true).secure(true).sameSite("Strict").path("/").maxAge(0).build();
    }

    public void setAll(HttpServletResponse response, String accessToken, Duration accessTtl,
                        String refreshToken, Duration refreshTtl, boolean rememberMe, String csrfToken) {
        response.addHeader("Set-Cookie", buildAccessCookie(accessToken, accessTtl).toString());
        response.addHeader("Set-Cookie", buildRefreshCookie(refreshToken, refreshTtl, rememberMe).toString());
        response.addHeader("Set-Cookie", buildCsrfCookie(csrfToken, refreshTtl).toString());
    }

    public void clearAll(HttpServletResponse response) {
        response.addHeader("Set-Cookie", clearCookie(ACCESS_COOKIE).toString());
        response.addHeader("Set-Cookie", clearCookie(REFRESH_COOKIE).toString());
        // CSRF cookie isn't HttpOnly, but clearing it the same way (Max-Age=0) is still correct.
        response.addHeader("Set-Cookie", ResponseCookie.from(CSRF_COOKIE, "").secure(true).sameSite("Strict").path("/").maxAge(0).build().toString());
    }

    public String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (name.equals(c.getName())) return c.getValue();
        }
        return null;
    }
}
