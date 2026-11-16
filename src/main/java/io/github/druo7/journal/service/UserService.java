package io.github.druo7.journal.service;

import io.github.druo7.journal.crypto.DataCipher;
import io.github.druo7.journal.dto.AuthDtos.*;
import io.github.druo7.journal.dto.PageResponse;
import io.github.druo7.journal.entity.Role;
import io.github.druo7.journal.entity.User;
import io.github.druo7.journal.exception.ApiException;
import io.github.druo7.journal.repository.JournalEntryRepository;
import io.github.druo7.journal.repository.UserRepository;
import io.github.druo7.journal.security.JwtService;
import io.github.druo7.journal.security.LoginRateLimiter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserService {
    public static final String EMAIL_FIELD = "users.email";
    public static final String CITY_FIELD = "users.city";

    private final UserRepository users;
    private final JournalEntryRepository entries;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final DataCipher cipher;
    private final LoginRateLimiter rateLimiter;
    /** Compared against when the username does not exist, so response time does not reveal which usernames exist. */
    private final String dummyHash;

    public UserService(UserRepository users, JournalEntryRepository entries, PasswordEncoder passwordEncoder,
                       JwtService jwtService, DataCipher cipher, LoginRateLimiter rateLimiter) {
        this.users = users;
        this.entries = entries;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.cipher = cipher;
        this.rateLimiter = rateLimiter;
        this.dummyHash = passwordEncoder.encode("0".repeat(64));
    }

    @Transactional
    public AuthResponse signup(SignupRequest req) {
        User user = createUser(req.username(), req.authKey(), req.wrappedKey(), req.wrappedKeyIv(), EnumSet.of(Role.USER));
        String email = blankToNull(req.email());
        if (req.wantsWeeklyReport() && email == null) {
            throw ApiException.badRequest("The weekly report needs an email address");
        }
        user.setEmailEnc(cipher.encrypt(email, EMAIL_FIELD));
        user.setCityEnc(cipher.encrypt(blankToNull(req.city()), CITY_FIELD));
        user.setWeeklyReport(req.wantsWeeklyReport());
        users.saveAndFlush(user);
        return authResponse(user);
    }

    @Transactional
    public AdminUserView createAdmin(SignupRequest req) {
        User user = createUser(req.username(), req.authKey(), req.wrappedKey(), req.wrappedKeyIv(), EnumSet.of(Role.USER, Role.ADMIN));
        users.saveAndFlush(user);
        return adminView(user);
    }

    private User createUser(String username, String authKey, String wrappedKey, String wrappedKeyIv, Set<Role> roles) {
        if (users.usernameTaken(username)) {
            throw ApiException.conflict("Username is already taken");
        }
        // A concurrent signup with the same name is still caught by the unique index (-> 409 via handler).
        return new User(username, passwordEncoder.encode(authKey), wrappedKey, wrappedKeyIv, roles);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req, String clientIp) {
        if (rateLimiter.isBlocked(req.username(), clientIp)) {
            throw ApiException.tooManyRequests("Too many failed attempts. Try again later.");
        }
        User user = users.findByUsernameCaseInsensitive(req.username()).orElse(null);
        boolean ok = passwordEncoder.matches(req.authKey(), user == null ? dummyHash : user.getAuthHash());
        if (user == null || !ok) {
            rateLimiter.recordFailure(req.username(), clientIp);
            throw ApiException.unauthorized("Incorrect username or password");
        }
        rateLimiter.recordSuccess(req.username());
        return authResponse(user);
    }

    @Transactional(readOnly = true)
    public MeResponse me(UUID userId) {
        return toMe(load(userId));
    }

    @Transactional
    public MeResponse updateProfile(UUID userId, UpdateProfileRequest req) {
        User user = load(userId);
        if (req.email() != null) {
            user.setEmailEnc(cipher.encrypt(blankToNull(req.email()), EMAIL_FIELD));
        }
        if (req.city() != null) {
            user.setCityEnc(cipher.encrypt(blankToNull(req.city()), CITY_FIELD));
        }
        if (req.weeklyReport() != null) {
            if (!req.weeklyReport() && user.isWeeklyReport()) {
                // Opting out deletes the server-readable mood copies, not just future ones.
                entries.clearReportSentiments(userId);
            }
            user.setWeeklyReport(req.weeklyReport());
        }
        if (user.isWeeklyReport() && user.getEmailEnc() == null) {
            throw ApiException.badRequest("The weekly report needs an email address");
        }
        return toMe(user);
    }

    /**
     * Change username and/or password. The client re-derives its keys from the new credentials and
     * re-wraps the same data key, so existing entries stay readable. All existing tokens are revoked.
     */
    @Transactional
    public AuthResponse changeCredentials(UUID userId, ChangeCredentialsRequest req, String clientIp) {
        User user = load(userId);
        verifyReauth(user, req.currentAuthKey(), clientIp);
        if (users.usernameTakenByOther(req.newUsername(), userId)) {
            throw ApiException.conflict("Username is already taken");
        }
        user.setUsername(req.newUsername());
        user.setAuthHash(passwordEncoder.encode(req.newAuthKey()));
        user.setWrappedKey(req.newWrappedKey());
        user.setWrappedKeyIv(req.newWrappedKeyIv());
        user.bumpTokenVersion();
        users.saveAndFlush(user);
        return authResponse(user);
    }

    @Transactional
    public void logoutEverywhere(UUID userId) {
        load(userId).bumpTokenVersion();
    }

    /** Deletes the account; journal entries and roles go with it (ON DELETE CASCADE). */
    @Transactional
    public void deleteAccount(UUID userId, ReauthRequest req, String clientIp) {
        User user = load(userId);
        verifyReauth(user, req.authKey(), clientIp);
        users.delete(user);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserView> listUsers(int page, int size) {
        var p = users.findAll(PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return PageResponse.of(p, UserService::adminView);
    }

    private void verifyReauth(User user, String authKey, String clientIp) {
        if (rateLimiter.isBlocked(user.getUsername(), clientIp)) {
            throw ApiException.tooManyRequests("Too many failed attempts. Try again later.");
        }
        if (!passwordEncoder.matches(authKey, user.getAuthHash())) {
            rateLimiter.recordFailure(user.getUsername(), clientIp);
            // 403 rather than 401: the session is valid, the confirmation is wrong.
            throw new ApiException(HttpStatus.FORBIDDEN, "Current password is incorrect");
        }
    }

    private User load(UUID id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
    }

    private AuthResponse authResponse(User user) {
        JwtService.IssuedToken t = jwtService.issue(user);
        return new AuthResponse(t.value(), t.expiresAt(), toMe(user));
    }

    MeResponse toMe(User u) {
        return new MeResponse(u.getId(), u.getUsername(), roleNames(u.getRoles()),
                cipher.decrypt(u.getEmailEnc(), EMAIL_FIELD), cipher.decrypt(u.getCityEnc(), CITY_FIELD),
                u.isWeeklyReport(), u.getWrappedKey(), u.getWrappedKeyIv(), u.getKdfVersion());
    }

    private static AdminUserView adminView(User u) {
        return new AdminUserView(u.getId(), u.getUsername(), roleNames(u.getRoles()), u.getCreatedAt());
    }

    private static Set<String> roleNames(Set<Role> roles) {
        return roles.stream().map(Enum::name).collect(Collectors.toCollection(java.util.TreeSet::new));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
