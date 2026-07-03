package com.vikisol.one.audit.service;

import com.vikisol.one.audit.entity.AuditLog;
import com.vikisol.one.audit.repository.AuditLogRepository;
import com.vikisol.one.security.service.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

// Real, database-backed audit trail. Previously the "Audit Logs" tab was 100% fake - 50
// randomly-generated mock rows with no backend at all. This records who did what, when, for the
// business events that actually matter (see call sites: employee creation, offer approval, salary
// changes, password resets, payroll runs, leave approvals, role changes, document deletion,
// settings changes).
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public void record(String action, String target, String details) {
        try {
            String email = null;
            String name = null;
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
                email = principal.getEmail();
                name = principal.getFirstName() + " " + principal.getLastName();
            }

            String ip = null;
            try {
                ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attrs != null) {
                    HttpServletRequest request = attrs.getRequest();
                    ip = request.getHeader("X-Forwarded-For");
                    if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
                }
            } catch (Exception ignored) {
                // No request context (e.g. called from a scheduled job) - fine to leave IP null.
            }

            auditLogRepository.save(AuditLog.builder()
                    .action(action)
                    .target(target)
                    .details(details)
                    .performedByEmail(email)
                    .performedByName(name)
                    .ipAddress(ip)
                    .timestamp(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            // An audit-log write failure should never break the actual business operation it's
            // attached to - log and move on.
            log.warn("Failed to record audit log for action '{}': {}", action, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getAll(Pageable pageable) {
        return auditLogRepository.findAllByOrderByTimestampDesc(pageable);
    }
}
