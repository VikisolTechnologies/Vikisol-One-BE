package com.vikisol.one.session.dto;

import com.vikisol.one.session.entity.ActiveSession;

import java.time.Instant;
import java.util.UUID;

public record ActiveSessionResponse(
        UUID id,
        String userEmail,
        Instant loginAt,
        Instant lastActivityAt,
        String ipAddress,
        String userAgent,
        boolean current
) {
    public static ActiveSessionResponse from(ActiveSession s, String currentJti) {
        return new ActiveSessionResponse(s.getId(), s.getUserEmail(), s.getLoginAt(), s.getLastActivityAt(),
                s.getIpAddress(), s.getUserAgent(), s.getJti().equals(currentJti));
    }
}
