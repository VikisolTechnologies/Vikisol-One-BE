package com.vikisol.one.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

    private SecretKey key;

    private static final String CLAIM_TYPE = "typ";
    public static final String TYPE_ACCESS = "access";
    // Issued after a correct password when MFA is enabled, before the real session exists -
    // deliberately a different claim value so this token can never be mistaken for (or misused
    // as) a real access token by JwtAuthenticationFilter, even though it's signed with the same key.
    public static final String TYPE_MFA_CHALLENGE = "mfa_challenge";

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public long defaultAccessTtlMs() {
        return jwtExpirationMs;
    }

    public String generateToken(Authentication authentication) {
        return generateAccessToken(authentication.getName(), Duration.ofMillis(jwtExpirationMs));
    }

    public String generateAccessToken(String email, Duration ttl) {
        return build(email, ttl, TYPE_ACCESS);
    }

    public String generateMfaChallengeToken(String email) {
        return build(email, Duration.ofMinutes(5), TYPE_MFA_CHALLENGE);
    }

    private String build(String email, Duration ttl, String type) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + ttl.toMillis());

        return Jwts.builder()
                .subject(email)
                .id(java.util.UUID.randomUUID().toString())
                .claim(CLAIM_TYPE, type)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    // Per-token session identifier (standard "jti" claim) - lets Active Sessions track/revoke one
    // specific token without invalidating every other session for the user (unlike the
    // passwordChangedAt-based invalidation, which is all-or-nothing).
    public String getJtiFromToken(String token) {
        return parseClaims(token).getId();
    }

    public String getEmailFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    public String getTypeFromToken(String token) {
        return parseClaims(token).get(CLAIM_TYPE, String.class);
    }

    // Used for session invalidation: a token issued before the user's last password change must
    // be rejected even though its signature/expiry are otherwise still valid.
    public java.time.Instant getIssuedAtFromToken(String token) {
        Date issuedAt = parseClaims(token).getIssuedAt();
        return issuedAt != null ? issuedAt.toInstant() : java.time.Instant.EPOCH;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            log.error("Invalid JWT token: {}", ex.getMessage());
            return false;
        }
    }

    // Validates AND checks the token was issued as an actual access token, not an MFA challenge
    // token that leaked into the wrong place - both are valid, signed JWTs, but only one should
    // ever be accepted by JwtAuthenticationFilter.
    public boolean validateAccessToken(String token) {
        if (!validateToken(token)) return false;
        return TYPE_ACCESS.equals(getTypeFromToken(token));
    }

    public boolean validateMfaChallengeToken(String token) {
        if (!validateToken(token)) return false;
        return TYPE_MFA_CHALLENGE.equals(getTypeFromToken(token));
    }
}
