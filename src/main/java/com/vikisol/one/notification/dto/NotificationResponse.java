package com.vikisol.one.notification.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID id, UUID recipientId, String title, String message,
        String type, UUID referenceId, String referenceType,
        boolean isRead, LocalDateTime readAt, LocalDateTime createdAt,
        String priority, String category, boolean archived, String deepLink
) {}
