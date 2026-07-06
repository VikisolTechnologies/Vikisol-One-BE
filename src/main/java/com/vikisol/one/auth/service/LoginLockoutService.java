package com.vikisol.one.auth.service;

import com.vikisol.one.audit.service.AuditService;
import com.vikisol.one.auth.entity.User;
import com.vikisol.one.auth.repository.UserRepository;
import com.vikisol.one.settings.dto.AuthSettingsDto;
import com.vikisol.one.settings.service.AuthSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

// Separate bean (not a private method on AuthService) so REQUIRES_NEW actually takes effect via
// Spring's proxy - a self-invoked method on the same class would silently run in AuthService.
// login()'s existing transaction instead, and get rolled back along with the BadCredentialsException
// it's recorded in response to, defeating the entire lockout mechanism.
@Service
@RequiredArgsConstructor
public class LoginLockoutService {

    private final UserRepository userRepository;
    private final AuditService auditService;
    private final AuthSettingsService authSettingsService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedAttempt(User user) {
        AuthSettingsDto settings = authSettingsService.getSettings();
        if (!settings.accountLockoutEnabled()) {
            auditService.record("Login Failed", user.getEmail(), "Account lockout is currently disabled in Authentication Settings");
            return;
        }
        int count = (user.getFailedLoginCount() != null ? user.getFailedLoginCount() : 0) + 1;
        user.setFailedLoginCount(count);
        if (count >= settings.maxFailedLoginAttempts()) {
            user.setLockedUntil(Instant.now().plusSeconds(settings.lockoutDurationMinutes() * 60L));
            auditService.record("Account Locked", user.getEmail(),
                    "Locked for " + settings.lockoutDurationMinutes() + " minutes after " + count + " failed login attempts");
        }
        userRepository.save(user);
        auditService.record("Login Failed", user.getEmail(), "Attempt " + count + " of " + settings.maxFailedLoginAttempts());
    }
}
