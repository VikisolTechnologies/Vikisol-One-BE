package com.vikisol.one.notification.entity;

import com.vikisol.one.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

// One row per user - unlike CompanySettings/AuthSettingsDto's flat org-wide key-value pattern,
// these toggles are personal (each employee decides their own reminders), so a dedicated
// per-user entity is a cleaner fit than trying to force it into the org-wide settings table.
@Entity
@Table(name = "notification_preferences", uniqueConstraints = @UniqueConstraint(columnNames = "user_id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
public class NotificationPreference extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Builder.Default
    private boolean emailNotifications = true;
    @Builder.Default
    private boolean pushNotifications = true;
    @Builder.Default
    private boolean leaveReminders = true;
    @Builder.Default
    private boolean timesheetReminders = true;
    @Builder.Default
    private boolean birthdayReminders = true;
    @Builder.Default
    private boolean interviewReminders = false;
    @Builder.Default
    private boolean payrollAlerts = false;
}
