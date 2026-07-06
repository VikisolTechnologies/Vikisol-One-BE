package com.vikisol.one.auth.service;

import com.vikisol.one.auth.entity.LoginHistoryEntry;
import com.vikisol.one.auth.repository.LoginHistoryEntryRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@RequiredArgsConstructor
public class LoginHistoryService {

    private final LoginHistoryEntryRepository loginHistoryEntryRepository;

    // REQUIRES_NEW for the same reason as LoginLockoutService.recordFailedAttempt: a LOGIN_FAILED
    // entry must survive even though the caller's transaction is about to be rolled back/exit via
    // the BadCredentialsException it's logging.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String userEmail, LoginHistoryEntry.EventType eventType, boolean success) {
        String ip = null;
        String userAgent = null;
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            ip = extractClientIp(request);
            userAgent = request.getHeader("User-Agent");
        }
        loginHistoryEntryRepository.save(LoginHistoryEntry.builder()
                .userEmail(userEmail)
                .eventType(eventType)
                .success(success)
                .ipAddress(ip)
                .userAgent(userAgent)
                .build());
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
