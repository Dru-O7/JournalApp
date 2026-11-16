package net.engineeringdigest.journalApp.crypto;

import net.engineeringdigest.journalApp.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataCipherTest {

    static AppProperties props(String dataKey) {
        return new AppProperties(new AppProperties.Jwt("x", Duration.ofMinutes(1), "i"), dataKey,
                new AppProperties.Cors(List.of()), new AppProperties.Weather("u", "", "c"),
                new AppProperties.Quote("q"), new AppProperties.LoginRateLimit(1, 1, Duration.ofMinutes(1)));
    }

    private final DataCipher cipher = new DataCipher(props(Base64.getEncoder().encodeToString(new byte[32])));

    @Test
    void roundTripsAndUsesFreshIvEachTime() {
        String a = cipher.encrypt("someone@example.com", "users.email");
        String b = cipher.encrypt("someone@example.com", "users.email");
        assertThat(a).startsWith("v1:").doesNotContain("someone").isNotEqualTo(b);
        assertThat(cipher.decrypt(a, "users.email")).isEqualTo("someone@example.com");
    }

    @Test
    void nullStaysNull() {
        assertThat(cipher.encrypt(null, "f")).isNull();
        assertThat(cipher.decrypt(null, "f")).isNull();
    }

    @Test
    void valueCannotBeMovedToAnotherColumn() {
        String email = cipher.encrypt("x@example.com", "users.email");
        assertThatThrownBy(() -> cipher.decrypt(email, "users.city")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void tamperingIsDetected() {
        String ct = cipher.encrypt("Pune", "users.city");
        byte[] raw = Base64.getDecoder().decode(ct.substring(3));
        raw[raw.length - 1] ^= 1;
        String tampered = "v1:" + Base64.getEncoder().encodeToString(raw);
        assertThatThrownBy(() -> cipher.decrypt(tampered, "users.city")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void wrongKeyCannotDecrypt() {
        byte[] other = new byte[32];
        other[0] = 1;
        DataCipher otherCipher = new DataCipher(props(Base64.getEncoder().encodeToString(other)));
        String ct = cipher.encrypt("secret", "f");
        assertThatThrownBy(() -> otherCipher.decrypt(ct, "f")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsWeakOrMalformedKeys() {
        assertThatThrownBy(() -> new DataCipher(props(Base64.getEncoder().encodeToString(new byte[16]))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new DataCipher(props("not base64!"))).isInstanceOf(IllegalStateException.class);
    }
}
