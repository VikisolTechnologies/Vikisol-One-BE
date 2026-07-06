package com.vikisol.one.communication.dto;

import com.vikisol.one.communication.entity.EmailLog;

import java.time.LocalDateTime;
import java.util.UUID;

public record EmailLogResponse(
        UUID id, String recipientEmail, String subject, String category, String status,
        UUID relatedEmployeeId, LocalDateTime sentAt, String errorMessage
) {
    public static EmailLogResponse from(EmailLog e) {
        return new EmailLogResponse(e.getId(), e.getRecipientEmail(), e.getSubject(),
                e.getCategory().name(), e.getStatus().name(), e.getRelatedEmployeeId(),
                e.getSentAt(), e.getErrorMessage());
    }
}
