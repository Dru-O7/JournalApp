package io.github.druo7.journal.crypto;

import io.github.druo7.journal.config.AppProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Server-side AES-256-GCM encryption for account fields the server itself must be able to read
 * (email for the weekly report, city for the weather greeting, opted-in mood for the report).
 * Protects against anyone with database access only; journal text never passes through here,
 * it is encrypted in the browser.
 *
 * Format: "v1:" + base64(iv[12] || ciphertext+tag). The field name is bound as AAD so a value
 * cannot be swapped between columns.
 */
@Component
public class DataCipher {
    private static final String PREFIX = "v1:";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public DataCipher(AppProperties props) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(props.dataKey());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("app.data-key must be base64", e);
        }
        if (raw.length != 32) {
            throw new IllegalStateException("app.data-key must decode to exactly 32 bytes (AES-256)");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public String encrypt(String plaintext, String field) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            c.updateAAD(field.getBytes(StandardCharsets.UTF_8));
            byte[] ct = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(IV_LEN + ct.length).put(iv).put(ct).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public String decrypt(String stored, String field) {
        if (stored == null) {
            return null;
        }
        if (!stored.startsWith(PREFIX)) {
            throw new IllegalStateException("Unknown ciphertext format for " + field);
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_LEN));
            c.updateAAD(field.getBytes(StandardCharsets.UTF_8));
            return new String(c.doFinal(all, IV_LEN, all.length - IV_LEN), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Decryption failed for " + field, e);
        }
    }
}
