package com.vikisol.one.mfa.service;

import com.vikisol.one.integration.util.CryptoUtil;
import com.vikisol.one.mfa.entity.MfaSecret;
import com.vikisol.one.mfa.repository.MfaSecretRepository;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import dev.samstevens.totp.util.Utils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// TOTP-based MFA (RFC 6238, the standard Google/Microsoft/Authy authenticator-app protocol).
// Self-service, opt-in for everyone - see AuthSettingsDto.mfaNudgedRoles for the "please enable
// this" nudge shown to CEO/HR_MANAGER/ADMIN in the Security Dashboard, which is deliberately a
// UI nudge, not a login-blocking requirement (avoids any risk of locking out the CEO on rollout).
@Service
@RequiredArgsConstructor
public class MfaService {

    private final MfaSecretRepository repository;
    private final CryptoUtil cryptoUtil;
    private final PasswordEncoder passwordEncoder;

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final QrGenerator qrGenerator = new ZxingPngQrGenerator();
    private final TimeProvider timeProvider = new SystemTimeProvider();
    private final CodeVerifier codeVerifier = new DefaultCodeVerifier(new DefaultCodeGenerator(), timeProvider);

    private static final String BACKUP_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public record SetupResult(String qrCodeDataUri, String manualEntryKey) {}

    public boolean isEnabled(UUID userId) {
        return repository.findByUserId(userId).map(MfaSecret::isEnabled).orElse(false);
    }

    // Generates (or regenerates, if setup was abandoned before) a fresh secret and its QR code -
    // NOT enabled yet, that only happens once the first real code is verified in enable().
    @Transactional
    public SetupResult startSetup(UUID userId, String email) {
        String secret = secretGenerator.generate();
        MfaSecret entity = repository.findByUserId(userId).orElse(MfaSecret.builder().userId(userId).build());
        entity.setSecretKeyEncrypted(cryptoUtil.encrypt(secret));
        entity.setEnabled(false);
        entity.setBackupCodeHashes(new ArrayList<>());
        repository.save(entity);

        QrData data = new QrData.Builder()
                .label(email)
                .secret(secret)
                .issuer("Vikisol One")
                .algorithm(dev.samstevens.totp.code.HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();
        try {
            byte[] imageData = qrGenerator.generate(data);
            String dataUri = Utils.getDataUriForImage(imageData, qrGenerator.getImageMimeType());
            return new SetupResult(dataUri, secret);
        } catch (Exception e) {
            throw new IllegalStateException("Could not generate MFA QR code", e);
        }
    }

    // Verifies the first code and flips the factor on - returns the one-time-visible backup
    // codes (raw, never retrievable again once this call returns; only their hashes persist).
    @Transactional
    public List<String> enable(UUID userId, String code) {
        MfaSecret entity = repository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("Run MFA setup first"));
        String secret = cryptoUtil.decrypt(entity.getSecretKeyEncrypted());
        if (!codeVerifier.isValidCode(secret, code)) {
            throw new IllegalArgumentException("Invalid verification code");
        }
        List<String> rawCodes = generateBackupCodes();
        entity.setEnabled(true);
        entity.setBackupCodeHashes(rawCodes.stream().map(passwordEncoder::encode).toList());
        repository.save(entity);
        return rawCodes;
    }

    @Transactional
    public void disable(UUID userId) {
        repository.findByUserId(userId).ifPresent(repository::delete);
    }

    // Used mid-login (POST /auth/mfa/verify) and also available for step-up confirmation flows -
    // accepts either a live 6-digit TOTP code or a single-use backup code.
    @Transactional
    public boolean verifyLogin(UUID userId, String code) {
        Optional<MfaSecret> maybe = repository.findByUserId(userId);
        if (maybe.isEmpty() || !maybe.get().isEnabled()) return false;
        MfaSecret entity = maybe.get();

        String secret = cryptoUtil.decrypt(entity.getSecretKeyEncrypted());
        if (codeVerifier.isValidCode(secret, code)) return true;

        // Fall back to backup codes - each one works exactly once.
        List<String> hashes = entity.getBackupCodeHashes();
        for (String hash : hashes) {
            if (passwordEncoder.matches(code, hash)) {
                List<String> remaining = new ArrayList<>(hashes);
                remaining.remove(hash);
                entity.setBackupCodeHashes(remaining);
                repository.save(entity);
                return true;
            }
        }
        return false;
    }

    private List<String> generateBackupCodes() {
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            StringBuilder sb = new StringBuilder(10);
            for (int j = 0; j < 10; j++) {
                if (j == 5) sb.append('-');
                sb.append(BACKUP_CODE_CHARS.charAt(RANDOM.nextInt(BACKUP_CODE_CHARS.length())));
            }
            codes.add(sb.toString());
        }
        return codes;
    }
}
