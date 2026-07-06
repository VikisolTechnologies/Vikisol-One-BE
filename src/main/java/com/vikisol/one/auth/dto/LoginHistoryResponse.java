package com.vikisol.one.auth.dto;

import com.vikisol.one.auth.entity.LoginHistoryEntry;

import java.time.LocalDateTime;
import java.util.UUID;

public record LoginHistoryResponse(
        UUID id,
        String userEmail,
        LoginHistoryEntry.EventType eventType,
        boolean success,
        String ipAddress,
        String userAgent,
        LocalDateTime timestamp
) {
    public static LoginHistoryResponse from(LoginHistoryEntry e) {
        return new LoginHistoryResponse(e.getId(), e.getUserEmail(), e.getEventType(), e.isSuccess(), e.getIpAddress(), e.getUserAgent(), e.getCreatedAt());
    }
}
