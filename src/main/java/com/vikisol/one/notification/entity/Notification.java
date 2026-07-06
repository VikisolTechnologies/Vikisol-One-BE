package com.vikisol.one.notification.entity;

import com.vikisol.one.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(callSuper = true)
public class Notification extends BaseEntity {

    @Column(nullable = false)
    private UUID recipientId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    private UUID referenceId;
    private String referenceType;

    @Column(nullable = false)
    private boolean isRead = false;

    private LocalDateTime readAt;

    // Nullable, no default at entity level - existing rows simply have priority = null,
    // frontend treats missing priority as "not set"/normal.
    @Enumerated(EnumType.STRING)
    private Priority priority;

    // Free-text category tag for filtering (separate from the structured `type` enum above,
    // since categories like "Policy Update" don't map cleanly onto NotificationType).
    private String category;

    // Safe on an existing table: DB-level default supplies the value for pre-existing rows.
    @Column(columnDefinition = "boolean default false")
    private Boolean archived;

    // Frontend route to navigate to when the notification is clicked, e.g. "/offboarding/{caseId}".
    private String deepLink;

    public enum NotificationType {
        LEAVE, ATTENDANCE, PAYROLL, TICKET, PERFORMANCE, GENERAL, RECRUITMENT, PROJECT, TIMESHEET, OFFBOARDING
    }

    public enum Priority {
        LOW, MEDIUM, HIGH
    }
}
