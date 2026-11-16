package net.engineeringdigest.journalApp.config;

import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/** Cache key = SHA-256 of the (normalised) arguments, so user-supplied values never appear in Redis keys. */
@Component("hashedKeyGenerator")
public class HashedKeyGenerator implements KeyGenerator {
    @Override
    public Object generate(Object target, Method method, Object... params) {
        StringBuilder sb = new StringBuilder(method.getName());
        for (Object p : params) {
            sb.append('|').append(p == null ? "" : p.toString().trim().toLowerCase(Locale.ROOT));
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
