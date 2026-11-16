package net.engineeringdigest.journalApp;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import net.engineeringdigest.journalApp.service.ExternalInfoClient;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiIntegrationTest extends IntegrationTest {
    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final String IV = "AAAAAAAAAAAAAAAA"; // 12 bytes, base64

    // ---------- helpers ----------

    record Session(String id, String username, String token, String authKey) {
    }

    static String uniqueName(String prefix) {
        return prefix + "_" + SEQ.incrementAndGet() + "_" + (System.nanoTime() % 100000);
    }

    static String hexKey(char c) {
        return String.valueOf(c).repeat(64);
    }

    Session signup(String username, String extraJson) throws Exception {
        String authKey = hexKey('a');
        String body = """
                {"username":"%s","authKey":"%s","wrappedKey":"d3JhcHBlZGtleQ==","wrappedKeyIv":"%s"%s}
                """.formatted(username, authKey, IV, extraJson == null ? "" : "," + extraJson);
        String json = mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Session(JsonPath.read(json, "$.user.id"), username, JsonPath.read(json, "$.token"), authKey);
    }

    Session signup(String prefix) throws Exception {
        return signup(uniqueName(prefix), null);
    }

    static MockHttpServletRequestBuilder as(Session s, MockHttpServletRequestBuilder b) {
        return b.header("Authorization", "Bearer " + s.token()).contentType(MediaType.APPLICATION_JSON);
    }

    String createEntry(Session s, String sentimentJson) throws Exception {
        String id = UUID.randomUUID().toString();
        String body = """
                {"id":"%s","ciphertext":"Y2lwaGVydGV4dA==","iv":"%s"%s}
                """.formatted(id, IV, sentimentJson == null ? "" : "," + sentimentJson);
        mvc.perform(as(s, post("/api/v1/entries")).content(body)).andExpect(status().isCreated());
        return id;
    }

    ResultActions login(String username, String authKey) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"authKey\":\"%s\"}".formatted(username, authKey)));
    }

    void makeAdmin(Session s) {
        jdbc.update("INSERT INTO user_roles (user_id, role) VALUES (?::uuid, 'ADMIN')", s.id());
    }

    // ---------- authentication ----------

    @Nested
    class Authentication {
        @Test
        void signupThenLoginThenMe() throws Exception {
            Session s = signup("alice");
            login(s.username().toUpperCase(), s.authKey())   // usernames are case-insensitive
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty())
                    .andExpect(jsonPath("$.user.wrappedKey").value("d3JhcHBlZGtleQ=="));
            mvc.perform(as(s, get("/api/v1/me")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value(s.username()))
                    .andExpect(jsonPath("$.roles[0]").value("USER"));
        }

        @Test
        void duplicateUsernameIsRejectedCaseInsensitively() throws Exception {
            Session s = signup("bob");
            mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                            {"username":"%s","authKey":"%s","wrappedKey":"eA==","wrappedKeyIv":"%s"}
                            """.formatted(s.username().toUpperCase(), hexKey('b'), IV)))
                    .andExpect(status().isConflict());
        }

        @Test
        void invalidSignupPayloadIs400WithFieldErrors() throws Exception {
            mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"x\",\"authKey\":\"not-hex\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.username").exists())
                    .andExpect(jsonPath("$.errors.authKey").exists());
        }

        @Test
        void weeklyReportWithoutEmailIsRejected() throws Exception {
            mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                            {"username":"%s","authKey":"%s","wrappedKey":"eA==","wrappedKeyIv":"%s","weeklyReport":true}
                            """.formatted(uniqueName("nomail"), hexKey('a'), IV)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void wrongPasswordIs401AndRepeatedFailuresAreRateLimited() throws Exception {
            Session s = signup("carol");
            for (int i = 0; i < 5; i++) {
                login(s.username(), hexKey('f')).andExpect(status().isUnauthorized());
            }
            // limit reached: even the correct password is refused until the window passes
            login(s.username(), s.authKey()).andExpect(status().isTooManyRequests());
        }

        @Test
        void unknownUserGetsSame401AsWrongPassword() throws Exception {
            login(uniqueName("ghost"), hexKey('a')).andExpect(status().isUnauthorized());
        }

        @Test
        void missingGarbageAndExpiredTokensAre401() throws Exception {
            Session s = signup("dave");
            mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/v1/me").header("Authorization", "Bearer abc.def.ghi")).andExpect(status().isUnauthorized());

            byte[] key = Base64.getDecoder().decode("dGVzdC1vbmx5LWp3dC1zZWNyZXQtMzItYnl0ZXMhISE=");
            JwtEncoder enc = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(key, "HmacSHA256")));
            String expired = enc.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                    JwtClaimsSet.builder().issuer("journal-app").subject(s.id()).claim("tv", 0)
                            .issuedAt(Instant.now().minusSeconds(7200)).expiresAt(Instant.now().minusSeconds(3600)).build()))
                    .getTokenValue();
            mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + expired)).andExpect(status().isUnauthorized());
        }

        @Test
        void tokenSignedWithAnotherKeyIsRejected() throws Exception {
            Session s = signup("erin");
            byte[] otherKey = new byte[32];
            JwtEncoder enc = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(otherKey, "HmacSHA256")));
            String forged = enc.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                    JwtClaimsSet.builder().issuer("journal-app").subject(s.id()).claim("tv", 0)
                            .expiresAt(Instant.now().plusSeconds(600)).build())).getTokenValue();
            mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + forged)).andExpect(status().isUnauthorized());
        }

        @Test
        void noSessionCookieIsEverIssued() throws Exception {
            Session s = signup("frank");
            MvcResult r = mvc.perform(as(s, get("/api/v1/me"))).andExpect(status().isOk()).andReturn();
            assertThat(r.getResponse().getHeader("Set-Cookie")).isNull();
            assertThat(r.getRequest().getSession(false)).isNull();
        }
    }

    // ---------- journal entries & isolation ----------

    @Nested
    class Entries {
        @Test
        void crudOnOwnEntries() throws Exception {
            Session s = signup("gina");
            String id = createEntry(s, null);
            mvc.perform(as(s, get("/api/v1/entries/" + id)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ciphertext").value("Y2lwaGVydGV4dA=="))
                    .andExpect(jsonPath("$.version").value(0));
            mvc.perform(as(s, put("/api/v1/entries/" + id))
                            .content("{\"ciphertext\":\"bmV3\",\"iv\":\"%s\",\"version\":0}".formatted(IV)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ciphertext").value("bmV3"))
                    .andExpect(jsonPath("$.version").value(1));
            mvc.perform(as(s, delete("/api/v1/entries/" + id))).andExpect(status().isNoContent());
            mvc.perform(as(s, get("/api/v1/entries/" + id))).andExpect(status().isNotFound());
        }

        @Test
        void usersCannotSeeOrTouchEachOthersEntries() throws Exception {
            Session alice = signup("alice");
            Session bob = signup("bob");
            String alicesEntry = createEntry(alice, null);
            createEntry(bob, null);

            mvc.perform(as(bob, get("/api/v1/entries/" + alicesEntry))).andExpect(status().isNotFound());
            mvc.perform(as(bob, put("/api/v1/entries/" + alicesEntry))
                            .content("{\"ciphertext\":\"cHduZWQ=\",\"iv\":\"%s\",\"version\":0}".formatted(IV)))
                    .andExpect(status().isNotFound());
            mvc.perform(as(bob, delete("/api/v1/entries/" + alicesEntry))).andExpect(status().isNotFound());

            // alice's entry is untouched
            mvc.perform(as(alice, get("/api/v1/entries/" + alicesEntry)))
                    .andExpect(jsonPath("$.ciphertext").value("Y2lwaGVydGV4dA=="));
            // each list contains only the owner's entry
            mvc.perform(as(bob, get("/api/v1/entries")))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.items[0].id").value(org.hamcrest.Matchers.not(alicesEntry)));
        }

        @Test
        void reusingAnotherUsersEntryIdConflictsWithoutOverwriting() throws Exception {
            Session alice = signup("alice");
            Session bob = signup("bob");
            String id = createEntry(alice, null);
            mvc.perform(as(bob, post("/api/v1/entries"))
                            .content("{\"id\":\"%s\",\"ciphertext\":\"cHduZWQ=\",\"iv\":\"%s\"}".formatted(id, IV)))
                    .andExpect(status().isConflict());
            mvc.perform(as(alice, get("/api/v1/entries/" + id))).andExpect(jsonPath("$.ciphertext").value("Y2lwaGVydGV4dA=="));
        }

        @Test
        void staleVersionIsRejectedWith409() throws Exception {
            Session s = signup("hank");
            String id = createEntry(s, null);
            String upd = "{\"ciphertext\":\"bmV3\",\"iv\":\"%s\",\"version\":0}".formatted(IV);
            mvc.perform(as(s, put("/api/v1/entries/" + id)).content(upd)).andExpect(status().isOk());
            mvc.perform(as(s, put("/api/v1/entries/" + id)).content(upd)).andExpect(status().isConflict());
        }

        @Test
        void malformedIdAndOversizedPayloadAreRejected() throws Exception {
            Session s = signup("ivy");
            mvc.perform(as(s, get("/api/v1/entries/not-a-uuid"))).andExpect(status().isBadRequest());
            mvc.perform(as(s, get("/api/v1/entries?size=1000"))).andExpect(status().isBadRequest());
            String big = "A".repeat(600 * 1024);
            mvc.perform(as(s, post("/api/v1/entries"))
                            .content("{\"id\":\"%s\",\"ciphertext\":\"%s\",\"iv\":\"%s\"}".formatted(UUID.randomUUID(), big, IV)))
                    .andExpect(status().isPayloadTooLarge());
        }

        @Test
        void listIsPaginatedNewestFirst() throws Exception {
            Session s = signup("jack");
            String first = createEntry(s, null);
            String second = createEntry(s, null);
            mvc.perform(as(s, get("/api/v1/entries?size=1")))
                    .andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.items[0].id").value(second))
                    .andExpect(jsonPath("$.totalPages").value(2));
            mvc.perform(as(s, get("/api/v1/entries?size=1&page=1"))).andExpect(jsonPath("$.items[0].id").value(first));
        }
    }

    // ---------- data at rest ----------

    @Nested
    class DataAtRest {
        @Test
        void databaseHoldsNoReadableAccountDataOrMood() throws Exception {
            Session s = signup(uniqueName("kim"), "\"email\":\"kim.secret@example.com\",\"city\":\"Reykjavik\",\"weeklyReport\":true");
            createEntry(s, "\"reportSentiment\":\"ANXIOUS\"");

            String userRow = jdbc.queryForObject("SELECT row_to_json(u)::text FROM users u WHERE id = ?::uuid", String.class, s.id());
            assertThat(userRow).doesNotContain("kim.secret").doesNotContain("Reykjavik").doesNotContain(s.authKey());
            String entryRow = jdbc.queryForObject("SELECT row_to_json(e)::text FROM journal_entries e WHERE user_id = ?::uuid", String.class, s.id());
            assertThat(entryRow).doesNotContain("ANXIOUS");

            // ...while the owner still sees their own profile in clear
            mvc.perform(as(s, get("/api/v1/me")))
                    .andExpect(jsonPath("$.email").value("kim.secret@example.com"))
                    .andExpect(jsonPath("$.city").value("Reykjavik"));
        }

        @Test
        void moodIsNotStoredForUsersWhoDidNotOptIn() throws Exception {
            Session s = signup("lee");
            createEntry(s, "\"reportSentiment\":\"HAPPY\"");
            Integer stored = jdbc.queryForObject(
                    "SELECT count(*) FROM journal_entries WHERE user_id = ?::uuid AND report_sentiment_enc IS NOT NULL", Integer.class, s.id());
            assertThat(stored).isZero();
        }

        @Test
        void optingOutDeletesStoredMoods() throws Exception {
            Session s = signup(uniqueName("mia"), "\"email\":\"m@example.com\",\"weeklyReport\":true");
            createEntry(s, "\"reportSentiment\":\"SAD\"");
            mvc.perform(as(s, patch("/api/v1/me")).content("{\"weeklyReport\":false}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.weeklyReport").value(false));
            Integer stored = jdbc.queryForObject(
                    "SELECT count(*) FROM journal_entries WHERE user_id = ?::uuid AND report_sentiment_enc IS NOT NULL", Integer.class, s.id());
            assertThat(stored).isZero();
        }
    }

    // ---------- account lifecycle ----------

    @Nested
    class Account {
        @Test
        void changingCredentialsRevokesOldTokensAndKeepsRoles() throws Exception {
            Session s = signup("nina");
            makeAdmin(s);
            String newName = uniqueName("nina2");
            String json = mvc.perform(as(s, put("/api/v1/me/credentials")).content("""
                            {"currentAuthKey":"%s","newUsername":"%s","newAuthKey":"%s","newWrappedKey":"bmV3d3JhcA==","newWrappedKeyIv":"%s"}
                            """.formatted(s.authKey(), newName, hexKey('c'), IV)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user.roles").value(org.hamcrest.Matchers.hasItem("ADMIN")))
                    .andReturn().getResponse().getContentAsString();

            mvc.perform(as(s, get("/api/v1/me"))).andExpect(status().isUnauthorized());
            Session renewed = new Session(s.id(), newName, JsonPath.read(json, "$.token"), hexKey('c'));
            mvc.perform(as(renewed, get("/api/v1/me"))).andExpect(status().isOk());
            login(newName, hexKey('c')).andExpect(status().isOk()).andExpect(jsonPath("$.user.wrappedKey").value("bmV3d3JhcA=="));
            login(s.username(), s.authKey()).andExpect(status().isUnauthorized());
        }

        @Test
        void changingCredentialsRequiresCurrentPasswordAndFreeUsername() throws Exception {
            Session s = signup("omar");
            Session other = signup("olga");
            String tmpl = """
                    {"currentAuthKey":"%s","newUsername":"%s","newAuthKey":"%s","newWrappedKey":"eA==","newWrappedKeyIv":"%s"}
                    """;
            mvc.perform(as(s, put("/api/v1/me/credentials")).content(tmpl.formatted(hexKey('9'), s.username(), hexKey('c'), IV)))
                    .andExpect(status().isForbidden());
            mvc.perform(as(s, put("/api/v1/me/credentials")).content(tmpl.formatted(s.authKey(), other.username(), hexKey('c'), IV)))
                    .andExpect(status().isConflict());
        }

        @Test
        void logoutEverywhereRevokesTokens() throws Exception {
            Session s = signup("pam");
            mvc.perform(as(s, post("/api/v1/me/logout-everywhere"))).andExpect(status().isNoContent());
            mvc.perform(as(s, get("/api/v1/me"))).andExpect(status().isUnauthorized());
        }

        @Test
        void deletingAccountRemovesEntriesAndInvalidatesToken() throws Exception {
            Session s = signup("quinn");
            createEntry(s, null);
            createEntry(s, null);
            mvc.perform(as(s, delete("/api/v1/me")).content("{\"authKey\":\"%s\"}".formatted(hexKey('9'))))
                    .andExpect(status().isForbidden());
            mvc.perform(as(s, delete("/api/v1/me")).content("{\"authKey\":\"%s\"}".formatted(s.authKey())))
                    .andExpect(status().isNoContent());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM journal_entries WHERE user_id = ?::uuid", Integer.class, s.id())).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ?::uuid", Integer.class, s.id())).isZero();
            mvc.perform(as(s, get("/api/v1/me"))).andExpect(status().isUnauthorized());
        }

        @Test
        void greetingDegradesWhenExternalApisFail() throws Exception {
            Session s = signup("rob");
            when(externalInfoClient.weather(anyString())).thenReturn(null);
            when(externalInfoClient.quote()).thenReturn(null);
            mvc.perform(as(s, get("/api/v1/me/greeting")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Hi " + s.username()))
                    .andExpect(jsonPath("$.feelsLike").doesNotExist());

            when(externalInfoClient.weather("New Delhi")).thenReturn(new ExternalInfoClient.WeatherInfo(31, "Haze"));
            when(externalInfoClient.quote()).thenReturn(new ExternalInfoClient.QuoteInfo("q", "a"));
            mvc.perform(as(s, get("/api/v1/me/greeting")))
                    .andExpect(jsonPath("$.feelsLike").value(31))
                    .andExpect(jsonPath("$.quote").value("q"));
        }
    }

    // ---------- admin ----------

    @Nested
    class Admin {
        @Test
        void regularUsersCannotUseAdminEndpoints() throws Exception {
            Session s = signup("sam");
            mvc.perform(as(s, get("/api/v1/admin/users"))).andExpect(status().isForbidden());
        }

        @Test
        void adminSeesOnlyAccountMetadata() throws Exception {
            Session admin = signup("root");
            makeAdmin(admin);
            signup(uniqueName("tara"), "\"email\":\"tara@example.com\",\"city\":\"Oslo\"");
            String json = mvc.perform(as(admin, get("/api/v1/admin/users")))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            List<Map<String, Object>> items = JsonPath.read(json, "$.items");
            assertThat(items).isNotEmpty();
            assertThat(items).allSatisfy(u -> assertThat(u.keySet()).containsExactlyInAnyOrder("id", "username", "roles", "createdAt"));
            assertThat(json).doesNotContain("tara@example.com").doesNotContain("Oslo");
        }

        @Test
        void adminCanCreateAnotherAdmin() throws Exception {
            Session admin = signup("root");
            makeAdmin(admin);
            String name = uniqueName("ops");
            mvc.perform(as(admin, post("/api/v1/admin/users")).content("""
                            {"username":"%s","authKey":"%s","wrappedKey":"eA==","wrappedKeyIv":"%s"}
                            """.formatted(name, hexKey('d'), IV)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.roles").value(org.hamcrest.Matchers.hasItems("ADMIN", "USER")));
            login(name, hexKey('d')).andExpect(status().isOk());
        }
    }

    // ---------- operational ----------

    @Test
    void healthIsPublicAndUnknownPathsAreDenied() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }

    @Test
    void emailServiceIsNotCalledDuringNormalRequests() throws Exception {
        signup("uma");
        verify(emailService, never()).send(any(), any(), any());
    }
}
