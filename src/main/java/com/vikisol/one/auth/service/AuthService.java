package com.vikisol.one.auth.service;

import com.vikisol.one.auth.dto.*;
import com.vikisol.one.auth.entity.ActivationToken;
import com.vikisol.one.auth.entity.User;
import com.vikisol.one.auth.repository.ActivationTokenRepository;
import com.vikisol.one.auth.repository.UserRepository;
import com.vikisol.one.common.exception.BadRequestException;
import com.vikisol.one.security.jwt.JwtTokenProvider;
import com.vikisol.one.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final ActivationTokenRepository activationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    // Basic brute-force / credential-stuffing protection: lock out an email after too many failed
    // attempts within a rolling window. In-memory is fine for the current single-instance deployment;
    // if this ever runs on multiple instances, move this to Redis so limits are shared across them.
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCKOUT_WINDOW = Duration.ofMinutes(15);
    private final ConcurrentHashMap<String, LoginAttempts> failedAttempts = new ConcurrentHashMap<>();

    private record LoginAttempts(int count, Instant firstFailureAt) {}

    public AuthResponse login(LoginRequest request) {
        String emailKey = request.email().toLowerCase();
        LoginAttempts attempts = failedAttempts.get(emailKey);
        if (attempts != null && attempts.count() >= MAX_ATTEMPTS
                && Duration.between(attempts.firstFailureAt(), Instant.now()).compareTo(LOCKOUT_WINDOW) < 0) {
            throw new BadRequestException("Too many failed login attempts. Please try again in 15 minutes.");
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );
        } catch (BadCredentialsException e) {
            recordFailedAttempt(emailKey);
            throw e;
        }
        failedAttempts.remove(emailKey);

        String token = jwtTokenProvider.generateToken(authentication);
        String refreshToken = jwtTokenProvider.generateTokenFromEmail(request.email());

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadRequestException("User not found"));

        return new AuthResponse(token, refreshToken, user.getEmail(),
                user.getRole().name(), user.getFirstName(), user.getLastName());
    }

    private void recordFailedAttempt(String emailKey) {
        failedAttempts.compute(emailKey, (k, existing) -> {
            if (existing == null || Duration.between(existing.firstFailureAt(), Instant.now()).compareTo(LOCKOUT_WINDOW) >= 0) {
                return new LoginAttempts(1, Instant.now());
            }
            return new LoginAttempts(existing.count() + 1, existing.firstFailureAt());
        });
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request, UserPrincipal principal) {
        User user = userRepository.findByEmail(principal.getEmail())
                .orElseThrow(() -> new BadRequestException("User not found"));

        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            throw new BadRequestException("Old password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public ActivationTokenInfo inspectActivationToken(String token) {
        return activationTokenRepository.findByToken(token)
                .filter(t -> !t.isUsed() && t.getExpiresAt().isAfter(Instant.now()))
                .map(t -> new ActivationTokenInfo(true, t.getUser().getFirstName(), t.getUser().getEmail()))
                .orElseGet(() -> new ActivationTokenInfo(false, null, null));
    }

    @Transactional
    public void activateAccount(ActivateAccountRequest request) {
        ActivationToken activationToken = activationTokenRepository.findByToken(request.token())
                .orElseThrow(() -> new BadRequestException("This activation link is invalid"));
        if (activationToken.isUsed()) {
            throw new BadRequestException("This activation link has already been used");
        }
        if (activationToken.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("This activation link has expired. Please ask HR to resend it.");
        }

        User user = activationToken.getUser();
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setEnabled(true);
        userRepository.save(user);

        activationToken.setUsed(true);
        activationTokenRepository.save(activationToken);
    }
}
