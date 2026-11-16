package net.engineeringdigest.journalApp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Request/response bodies for authentication and account management.
 *
 * The browser derives two independent values from the password: an auth key (sent here, bcrypt-ed
 * by the server) and a key-encryption key (never sent) that wraps the user's random data key.
 */
public final class AuthDtos {
    private AuthDtos() {
    }

    public static final String USERNAME_REGEX = "^[A-Za-z0-9_.-]{3,32}$";
    public static final String AUTH_KEY_REGEX = "^[0-9a-f]{64}$";
    public static final String BASE64_REGEX = "^[A-Za-z0-9+/]+={0,2}$";

    public record SignupRequest(
            @NotBlank @Pattern(regexp = USERNAME_REGEX, message = "3-32 characters: letters, digits, _ . -") String username,
            @NotBlank @Pattern(regexp = AUTH_KEY_REGEX, message = "must be 64 lowercase hex characters") String authKey,
            @NotBlank @Size(max = 200) @Pattern(regexp = BASE64_REGEX) String wrappedKey,
            @NotBlank @Size(min = 16, max = 24) @Pattern(regexp = BASE64_REGEX) String wrappedKeyIv,
            @Email @Size(max = 254) String email,
            @Size(max = 100) String city,
            Boolean weeklyReport) {
        public boolean wantsWeeklyReport() {
            return Boolean.TRUE.equals(weeklyReport);
        }
    }

    public record LoginRequest(
            @NotBlank @Size(max = 32) String username,
            @NotBlank @Pattern(regexp = AUTH_KEY_REGEX, message = "must be 64 lowercase hex characters") String authKey) {
    }

    public record ChangeCredentialsRequest(
            @NotBlank @Pattern(regexp = AUTH_KEY_REGEX) String currentAuthKey,
            @NotBlank @Pattern(regexp = USERNAME_REGEX, message = "3-32 characters: letters, digits, _ . -") String newUsername,
            @NotBlank @Pattern(regexp = AUTH_KEY_REGEX) String newAuthKey,
            @NotBlank @Size(max = 200) @Pattern(regexp = BASE64_REGEX) String newWrappedKey,
            @NotBlank @Size(min = 16, max = 24) @Pattern(regexp = BASE64_REGEX) String newWrappedKeyIv) {
    }

    public record ReauthRequest(@NotBlank @Pattern(regexp = AUTH_KEY_REGEX) String authKey) {
    }

    /** Partial update: null fields are left unchanged; empty strings clear email/city. */
    public record UpdateProfileRequest(
            @Email @Size(max = 254) String email,
            @Size(max = 100) String city,
            Boolean weeklyReport) {
    }

    public record MeResponse(
            UUID id,
            String username,
            Set<String> roles,
            String email,
            String city,
            boolean weeklyReport,
            String wrappedKey,
            String wrappedKeyIv,
            int kdfVersion) {
    }

    public record AuthResponse(String token, Instant expiresAt, MeResponse user) {
    }

    public record GreetingResponse(String message, Integer feelsLike, String weatherDescription, String quote, String quoteAuthor) {
    }

    public record AdminUserView(UUID id, String username, Set<String> roles, Instant createdAt) {
    }
}
