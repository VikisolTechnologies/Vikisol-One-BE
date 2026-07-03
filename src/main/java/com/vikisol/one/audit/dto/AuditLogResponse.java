package com.vikisol.one.audit.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        String action,
        String target,
        String details,
        String performedByName,
        String performedByEmail,
        String ipAddress,
        LocalDateTime timestamp
) {
}
