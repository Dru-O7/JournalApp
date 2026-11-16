package net.engineeringdigest.journalApp.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * All application-specific configuration. Secrets come from environment variables
 * (see application.yml); startup fails if a required value is missing.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @Valid @NotNull Jwt jwt,
        /* Base64-encoded 256-bit key used to encrypt account data (email, city) at rest. */
        @NotBlank String dataKey,
        @Valid @NotNull Cors cors,
        @Valid @NotNull Weather weather,
        @Valid @NotNull Quote quote,
        @Valid @NotNull LoginRateLimit loginRateLimit
) {
    public record Jwt(
            /* Base64-encoded HMAC key, at least 256 bits. */
            @NotBlank String secret,
            @DefaultValue("60m") Duration ttl,
            @DefaultValue("journal-app") String issuer) {
    }

    public record Cors(@DefaultValue List<String> allowedOrigins) {
    }

    public record Weather(
            @DefaultValue("https://api.weatherstack.com/current") String baseUrl,
            /* Optional; when blank the greeting is returned without weather. */
            @DefaultValue("") String apiKey,
            @DefaultValue("New Delhi") String defaultCity) {
    }

    public record Quote(@DefaultValue("https://stoic.tekloon.net/stoic-quote") String url) {
    }

    public record LoginRateLimit(
            @Min(1) @DefaultValue("10") int maxAttemptsPerUsername,
            @Min(1) @DefaultValue("50") int maxAttemptsPerIp,
            @DefaultValue("15m") Duration window) {
    }
}
