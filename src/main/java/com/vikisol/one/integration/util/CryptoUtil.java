package com.vikisol.one.integration.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

// Reversible encryption for integration secrets (Azure client secret, SMTP password, etc.) at
// rest - unlike user passwords (one-way hashed via BCrypt), these need to be decrypted again to
// actually call the provider's API, so a symmetric cipher is the correct tool here, not a hash.
@Component
public class CryptoUtil {

    // Falls back to a fixed dev-only key (same pattern as the JWT secret's local-dev fallback in
    // application.yml) so local/dev environments work out of the box; production MUST set a real
    // INTEGRATION_ENCRYPTION_KEY or every deployment would share the same key.
    @Value("${app.integration-encryption-key:local-dev-only-integration-key-do-not-use-in-prod!!}")
    private String rawKey;

    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;

    private SecretKeySpec keySpec() {
        try {
            byte[] hashed = MessageDigest.getInstance("SHA-256").digest(rawKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return new SecretKeySpec(hashed, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Could not derive encryption key", e);
        }
    }

    public String encrypt(String plainText) {
        if (plainText == null) return null;
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public String decrypt(String encoded) {
        if (encoded == null) return null;
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            byte[] iv = new byte[IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTES);
            byte[] cipherText = new byte[combined.length - IV_LENGTH_BYTES];
            System.arraycopy(combined, IV_LENGTH_BYTES, cipherText, 0, cipherText.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(cipherText), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }
}
