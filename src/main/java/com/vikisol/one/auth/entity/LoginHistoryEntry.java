package com.vikisol.one.auth.entity;

import com.vikisol.one.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

// Structured, queryable login/security audit trail - separate from the generic AuditLog (which
// is a free-text action/target/details row aimed at "who changed what business record") since
// this needs its own dedicated Login History view with IP/browser/device and a fixed set of
// event types.
@Entity
// Every successful login runs a "have we seen this exact (email, ip, user-agent) combo before"
// existence check (see AuthService.maybeSendLoginAlert) - without an index covering those
// columns, that query does a full table scan on every single login, and this table only grows.
// As it accumulates rows over the life of the app, logins get measurably, silently slower over
// time even for a device/account that's logged in many times before.
@Table(name = "login_history_entries", indexes = {
        @Index(name = "idx_login_history_alert_lookup", columnList = "user_email, event_type, success, created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class LoginHistoryEntry extends BaseEntity {

    @Column(nullable = false)
    private String userEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventType eventType;

    @Column(nullable = false)
    private boolean success;

    private String ipAddress;

    @Column(columnDefinition = "TEXT")
    private String userAgent;

    public enum EventType {
        LOGIN_SUCCESS, LOGIN_FAILED, PASSWORD_RESET_REQUESTED, PASSWORD_RESET_COMPLETED,
        ACCOUNT_LOCKED, ACCOUNT_UNLOCKED, ACCOUNT_ACTIVATED, LOGOUT, SESSION_EXPIRED,
        // A revoked (already-rotated-away) refresh token was presented again - see
        // RefreshTokenService.rotate. Strong signal of a stolen/copied token; the whole session
        // family is revoked automatically when this happens.
        SESSION_REUSE_DETECTED,
        // MFA challenge issued (password correct, code not yet verified) / a wrong code entered.
        MFA_CHALLENGE_ISSUED, MFA_VERIFY_FAILED
    }
}
