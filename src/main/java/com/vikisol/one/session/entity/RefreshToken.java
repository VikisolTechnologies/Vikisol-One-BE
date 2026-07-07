package com.vikisol.one.session.entity;

import com.vikisol.one.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

// One row per issued refresh token. Rotated on every use (POST /auth/refresh): the presented
// token is marked revoked and a new row is inserted in the same familyId. If a token that's
// already revoked is ever presented again, that's a replay of a stolen/leaked token - the whole
// family is revoked immediately (see RefreshTokenService.rotate). The raw token value is never
// persisted, only its SHA-256 hash, so a database read alone can't be used to forge a session.
@Entity
@Table(name = "refresh_tokens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RefreshToken extends BaseEntity {

    @Column(nullable = false)
    private String userEmail;

    @Column(nullable = false, unique = true)
    private String tokenHash;

    @Column(nullable = false)
    private UUID familyId;

    @Column(nullable = false)
    private Instant issuedAt;

    @Column(nullable = false)
    private Instant expiresAt;

    @Builder.Default
    private boolean revoked = false;

    private String replacedByHash;

    private String createdIp;

    @Column(columnDefinition = "TEXT")
    private String createdUserAgent;

    // The matching ActiveSession row's jti, so revoking a refresh-token family (theft detected,
    // force-logout, password change) can also revoke the session it kept alive.
    private String sessionJti;

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }
}
