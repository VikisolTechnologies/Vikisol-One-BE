package com.vikisol.one.session.entity;

import com.vikisol.one.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// One row per issued JWT (keyed by its "jti" claim) so a specific device/session can be listed and
// revoked without invalidating every other session for the user - the passwordChangedAt-based
// check in JwtAuthenticationFilter is all-or-nothing and stays in place as a second, independent
// invalidation path (e.g. "log out everywhere" after a password change).
@Entity
@Table(name = "active_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ActiveSession extends BaseEntity {

    @Column(nullable = false)
    private String userEmail;

    @Column(nullable = false, unique = true)
    private String jti;

    @Column(nullable = false)
    private Instant loginAt;

    private Instant lastActivityAt;

    private String ipAddress;

    @Column(columnDefinition = "TEXT")
    private String userAgent;

    @Builder.Default
    private boolean revoked = false;
}
