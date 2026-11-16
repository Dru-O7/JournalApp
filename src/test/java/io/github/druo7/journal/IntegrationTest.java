package io.github.druo7.journal;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import io.github.druo7.journal.service.EmailService;
import io.github.druo7.journal.service.ExternalInfoClient;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Full application context against a real PostgreSQL (embedded binaries, no Docker needed), with
 * Flyway migrations applied. Third-party HTTP and SMTP are mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {
    private static final EmbeddedPostgres PG;

    static {
        try {
            PG = EmbeddedPostgres.builder().start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> PG.getJdbcUrl("postgres", "postgres"));
        r.add("spring.datasource.username", () -> "postgres");
        r.add("spring.datasource.password", () -> "postgres");
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @MockitoBean
    protected ExternalInfoClient externalInfoClient;

    @MockitoBean
    protected EmailService emailService;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE journal_entries, user_roles, users CASCADE");
    }
}
