package com.vikisol.one.notification.dto;

import com.vikisol.one.notification.entity.Notification.NotificationType;
import java.util.UUID;

public record CreateNotificationRequest(
        UUID recipientId, String title, String message,
        NotificationType type, UUID referenceId, String referenceType
) {}
