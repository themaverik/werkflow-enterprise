package com.werkflow.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption for connector secret_value at rest.
 * Single master key (WERKFLOW_MASTER_ENCRYPTION_KEY) — set once per deployment.
 * Format stored in DB: base64(iv) + ":" + base64(ciphertext+authTag)
 */
@Component
public class EncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final SecretKey secretKey;
    private final SecureRandom random = new SecureRandom();

    public EncryptionService(
            @Value("${werkflow.master-encryption-key:dev-insecure-key-32-bytes-padded}") String rawKey) {
        byte[] keyBytes = rawKey.getBytes();
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        } else if (keyBytes.length > 32) {
            byte[] trimmed = new byte[32];
            System.arraycopy(keyBytes, 0, trimmed, 0, 32);
            keyBytes = trimmed;
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) return null;
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes());
            return Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(ciphertext);
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || stored.isBlank()) return null;
        try {
            String[] parts = stored.split(":", 2);
            if (parts.length != 2) throw new IllegalArgumentException("Invalid encrypted value format");
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] ciphertext = Base64.getDecoder().decode(parts[1]);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(ciphertext));
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }
}
