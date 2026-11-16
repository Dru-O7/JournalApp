package net.engineeringdigest.journalApp.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import net.engineeringdigest.journalApp.config.AppProperties;
import net.engineeringdigest.journalApp.entity.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.Base64;

/**
 * Issues and verifies HS256 access tokens. The subject is the immutable user id (so renames do not
 * break sessions) and the "tv" claim carries the user's token version, checked on every request so
 * that credential changes and "log out everywhere" revoke existing tokens.
 */
@Component
public class JwtService {
    public static final String TOKEN_VERSION_CLAIM = "tv";

    private final AppProperties.Jwt cfg;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    public JwtService(AppProperties props) {
        this.cfg = props.jwt();
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(cfg.secret());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("app.jwt.secret must be base64", e);
        }
        if (raw.length < 32) {
            throw new IllegalStateException("app.jwt.secret must decode to at least 32 bytes");
        }
        SecretKey key = new SecretKeySpec(raw, "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        NimbusJwtDecoder d = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        d.setJwtValidator(JwtValidators.createDefaultWithIssuer(cfg.issuer()));
        this.decoder = d;
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }

    public IssuedToken issue(User user) {
        Instant now = Instant.now();
        Instant exp = now.plus(cfg.ttl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(cfg.issuer())
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(exp)
                .claim(TOKEN_VERSION_CLAIM, user.getTokenVersion())
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new IssuedToken(token, exp);
    }

    public JwtDecoder decoder() {
        return decoder;
    }
}
