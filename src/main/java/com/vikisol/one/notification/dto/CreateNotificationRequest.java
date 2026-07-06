package com.vikisol.one.notification.dto;

import com.vikisol.one.notification.entity.Notification.NotificationType;
import com.vikisol.one.notification.entity.Notification.Priority;
import java.util.UUID;

public record CreateNotificationRequest(
        UUID recipientId, String title, String message,
        NotificationType type, UUID referenceId, String referenceType,
        Priority priority, String category, String deepLink
) {
    // Back-compat for existing callers that don't set priority/category/deepLink.
    public CreateNotificationRequest(UUID recipientId, String title, String message,
                                      NotificationType type, UUID referenceId, String referenceType) {
        this(recipientId, title, message, type, referenceId, referenceType, null, null, null);
    }
}
