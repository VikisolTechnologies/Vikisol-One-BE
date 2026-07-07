package com.vikisol.one.mfa.entity;

import com.vikisol.one.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;
import java.util.UUID;

// One row per user who has ever started MFA setup. `enabled` only flips true once the first
// TOTP code is verified (see MfaService.enable) - a row existing with enabled=false is just an
// abandoned/in-progress setup attempt, not an active factor. secretKeyEncrypted is encrypted at
// rest via the same CryptoUtil already used for integration secrets (Azure client secret, etc.),
// never stored/logged in plaintext.
@Entity
@Table(name = "mfa_secrets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MfaSecret extends BaseEntity {

    @Column(nullable = false, unique = true)
    private UUID userId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String secretKeyEncrypted;

    @Builder.Default
    private boolean enabled = false;

    // One-time recovery codes, BCrypt-hashed (same one-way hashing as passwords - these grant
    // account access, so they get the same treatment, never reversible even at rest). Each is
    // removed from this list the moment it's redeemed.
    @ElementCollection
    @CollectionTable(name = "mfa_backup_codes", joinColumns = @JoinColumn(name = "mfa_secret_id"))
    @Column(name = "code_hash", columnDefinition = "TEXT")
    @Builder.Default
    private List<String> backupCodeHashes = new java.util.ArrayList<>();
}
