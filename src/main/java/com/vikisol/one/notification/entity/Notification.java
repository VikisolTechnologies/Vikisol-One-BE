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

    public enum NotificationType {
        LEAVE, ATTENDANCE, PAYROLL, TICKET, PERFORMANCE, GENERAL, RECRUITMENT, PROJECT, TIMESHEET, OFFBOARDING
    }
}
