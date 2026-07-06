package com.vikisol.one.auth.entity;

import com.vikisol.one.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

// Structured, queryable login/security audit trail - separate from the generic AuditLog (which
// is a free-text action/target/details row aimed at "who changed what business record") since
// this needs its own dedicated Login History view with IP/browser/device and a fixed set of
// event types.
@Entity
@Table(name = "login_history_entries")
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
        ACCOUNT_LOCKED, ACCOUNT_UNLOCKED, ACCOUNT_ACTIVATED, LOGOUT, SESSION_EXPIRED
    }
}
