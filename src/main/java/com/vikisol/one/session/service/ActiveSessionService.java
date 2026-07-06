package com.vikisol.one.session.service;

import com.vikisol.one.session.entity.ActiveSession;
import com.vikisol.one.session.repository.ActiveSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ActiveSessionService {

    private final ActiveSessionRepository repository;

    // REQUIRES_NEW so recording the session survives regardless of what the calling transaction
    // (AuthService.login) does afterwards, same reasoning as LoginHistoryService.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLogin(String userEmail, String jti) {
        String ip = null;
        String userAgent = null;
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            ip = extractClientIp(request);
            userAgent = request.getHeader("User-Agent");
        }
        Instant now = Instant.now();
        repository.save(ActiveSession.builder()
                .userEmail(userEmail)
                .jti(jti)
                .loginAt(now)
                .lastActivityAt(now)
                .ipAddress(ip)
                .userAgent(userAgent)
                .revoked(false)
                .build());
    }

    /** Returns false if the session has been revoked (or was never issued through this service, e.g. an old token). */
    @Transactional
    public boolean touch(String jti) {
        ActiveSession session = repository.findByJti(jti).orElse(null);
        if (session == null) return true; // tokens issued before this feature existed - don't lock everyone out
        if (session.isRevoked()) return false;
        // Throttled to avoid a DB write on every single authenticated request.
        Instant now = Instant.now();
        if (session.getLastActivityAt() == null || session.getLastActivityAt().isBefore(now.minusSeconds(60))) {
            session.setLastActivityAt(now);
            repository.save(session);
        }
        return true;
    }

    public List<ActiveSession> listForUser(String userEmail) {
        return repository.findByUserEmailAndRevokedFalseOrderByLastActivityAtDesc(userEmail);
    }

    public List<ActiveSession> listAll() {
        return repository.findAllByOrderByLastActivityAtDesc();
    }

    // requireOwnerEmail null = no ownership restriction (admin path); non-null = only revoke if
    // the session actually belongs to that user.
    @Transactional
    public void revoke(UUID sessionId, String requireOwnerEmail) {
        repository.findById(sessionId).ifPresent(s -> {
            if (requireOwnerEmail != null && !requireOwnerEmail.equalsIgnoreCase(s.getUserEmail())) {
                return;
            }
            s.setRevoked(true);
            repository.save(s);
        });
    }

    @Transactional
    public void revokeAllForUser(String userEmail) {
        repository.revokeAllForUser(userEmail);
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
