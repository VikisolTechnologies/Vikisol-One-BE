package com.vikisol.one.auth.entity;

import com.vikisol.one.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// One-time 6-digit login code (email OTP, the "OTP Login" tab on the sign-in page) - deliberately
// a separate entity from PasswordResetToken/ActivationToken: those are long random strings good
// for a clicked link, this is a short numeric code a person types in by hand, with a much shorter
// expiry (see AuthService.OTP_TTL) and a real short-lived login credential in its own right, not
// a password-recovery mechanism.
@Entity
@Table(name = "login_otps")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class LoginOtp extends BaseEntity {

    @Column(nullable = false)
    private String email;

    // SHA-256 hash, never the raw 6-digit code - same reasoning as RefreshToken.tokenHash: a
    // database read alone should never be enough to log in as someone.
    @Column(nullable = false)
    private String codeHash;

    @Column(nullable = false)
    private Instant expiresAt;

    @Builder.Default
    private boolean used = false;

    // Failed verification attempts against this specific code - capped low (see
    // AuthService.MAX_OTP_ATTEMPTS) so the 30-second window can't be brute-forced (1 in a million
    // per guess, but still worth a hard cap rather than relying on the clock alone).
    @Builder.Default
    private int attempts = 0;
}
