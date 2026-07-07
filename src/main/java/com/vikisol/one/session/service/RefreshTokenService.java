package com.vikisol.one.session.service;

import com.vikisol.one.session.entity.RefreshToken;
import com.vikisol.one.session.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Refresh-token rotation with reuse detection - the same pattern used by Auth0/Okta/most OAuth2
// providers: every redemption issues a brand-new token and immediately invalidates the one
// presented. If a revoked (already-rotated-away) token is ever presented again, that can only
// mean the token was copied/stolen and is now racing the legitimate holder - the entire rotation
// chain (familyId) is revoked on the spot rather than trusting either party.
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository repository;

    private static final SecureRandom RANDOM = new SecureRandom();

    public record IssuedToken(String rawValue, RefreshToken entity) {}

    public record RotationResult(boolean success, boolean reuseDetected, RefreshToken newToken, String rawValue, String userEmail) {}

    private String generateRawToken() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawValue) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawValue.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Could not hash refresh token", e);
        }
    }

    @Transactional
    public IssuedToken issueNew(String userEmail, UUID familyId, Instant expiresAt, String ip, String userAgent, String sessionJti) {
        String raw = generateRawToken();
        RefreshToken entity = repository.save(RefreshToken.builder()
                .userEmail(userEmail)
                .tokenHash(hash(raw))
                .familyId(familyId)
                .issuedAt(Instant.now())
                .expiresAt(expiresAt)
                .revoked(false)
                .createdIp(ip)
                .createdUserAgent(userAgent)
                .sessionJti(sessionJti)
                .build());
        return new IssuedToken(raw, entity);
    }

    // Starts a brand-new rotation chain - only at login.
    public IssuedToken issueForNewFamily(String userEmail, Instant expiresAt, String ip, String userAgent, String sessionJti) {
        return issueNew(userEmail, UUID.randomUUID(), expiresAt, ip, userAgent, sessionJti);
    }

    // sessionJti is unknown at rotation time (the new access token isn't minted until after this
    // succeeds, since we don't want to mint one for a rotation that turns out to be invalid) - the
    // caller fills it in afterwards via updateSessionJti.
    @Transactional
    public RotationResult rotate(String rawPresentedToken, Instant newExpiresAt, String ip, String userAgent) {
        Optional<RefreshToken> found = repository.findByTokenHash(hash(rawPresentedToken));
        if (found.isEmpty()) {
            return new RotationResult(false, false, null, null, null);
        }
        RefreshToken existing = found.get();
        if (existing.isExpired()) {
            return new RotationResult(false, false, null, null, null);
        }
        if (existing.isRevoked()) {
            // Reuse of an already-rotated-away token - treat the whole family as compromised.
            repository.revokeFamily(existing.getFamilyId());
            return new RotationResult(false, true, null, null, existing.getUserEmail());
        }

        existing.setRevoked(true);
        IssuedToken next = issueNew(existing.getUserEmail(), existing.getFamilyId(), newExpiresAt, ip, userAgent, null);
        existing.setReplacedByHash(next.entity().getTokenHash());
        repository.save(existing);

        return new RotationResult(true, false, next.entity(), next.rawValue(), existing.getUserEmail());
    }

    @Transactional
    public void updateSessionJti(RefreshToken token, String jti) {
        token.setSessionJti(jti);
        repository.save(token);
    }

    @Transactional
    public void revokeFamilyContaining(String rawToken) {
        repository.findByTokenHash(hash(rawToken)).ifPresent(t -> repository.revokeFamily(t.getFamilyId()));
    }

    // Revokes the refresh-token family backing one specific device/session - used when a user
    // revokes a single Active Session entry, so that device can't silently refresh back in.
    @Transactional
    public void revokeBySessionJti(String sessionJti) {
        repository.findBySessionJtiAndRevokedFalse(sessionJti)
                .forEach(t -> repository.revokeFamily(t.getFamilyId()));
    }

    @Transactional
    public void revokeAllForUser(String userEmail) {
        repository.revokeAllForUser(userEmail);
    }

    public long countActiveForUser(String userEmail) {
        return repository.countByUserEmailAndRevokedFalse(userEmail);
    }

    public List<RefreshToken> findFamily(UUID familyId) {
        return repository.findByFamilyId(familyId);
    }
}
