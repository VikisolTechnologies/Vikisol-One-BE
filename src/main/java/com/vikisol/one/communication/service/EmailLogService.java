package com.vikisol.one.communication.service;

import com.vikisol.one.common.dto.PagedResponse;
import com.vikisol.one.communication.dto.EmailLogResponse;
import com.vikisol.one.communication.entity.EmailLog;
import com.vikisol.one.communication.repository.EmailLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailLogService {

    private final EmailLogRepository emailLogRepository;

    // REQUIRES_NEW so a logging failure (or the outer @Async email transaction rolling back)
    // never affects, and is never affected by, the actual send outcome.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String recipientEmail, String subject, EmailLog.Category category,
                     EmailLog.Status status, UUID relatedEmployeeId, String errorMessage) {
        try {
            EmailLog entry = EmailLog.builder()
                    .recipientEmail(recipientEmail)
                    .subject(subject)
                    .category(category)
                    .status(status)
                    .relatedEmployeeId(relatedEmployeeId)
                    .sentAt(LocalDateTime.now())
                    .errorMessage(errorMessage)
                    .build();
            emailLogRepository.save(entry);
        } catch (Exception e) {
            // Never let audit-logging break the actual email send/failure path.
            log.warn("Failed to record EmailLog for {}: {}", recipientEmail, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public PagedResponse<EmailLogResponse> search(EmailLog.Category category, EmailLog.Status status,
                                                   LocalDateTime fromDate, LocalDateTime toDate,
                                                   String search, Pageable pageable) {
        Page<EmailLog> page = emailLogRepository.search(category, status, fromDate, toDate,
                (search != null && !search.isBlank()) ? search.trim() : null, pageable);
        return new PagedResponse<>(
                page.getContent().stream().map(EmailLogResponse::from).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.isLast());
    }
}
