package com.vikisol.one.auth.entity;

import com.vikisol.one.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// One-time, expiring token for the Forgot Password flow - mirrors ActivationToken's shape
// deliberately (same single-use/expiry semantics), but kept as a separate entity/table since the
// two flows are conceptually distinct (first-time account setup vs. an existing account's
// password recovery) and must never share tokens. Always emailed to the employee's personal
// address, never their official company email.
@Entity
@Table(name = "password_reset_tokens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PasswordResetToken extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Instant expiresAt;

    @Builder.Default
    private boolean used = false;
}
