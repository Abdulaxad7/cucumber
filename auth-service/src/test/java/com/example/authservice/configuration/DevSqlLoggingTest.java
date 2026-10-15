package com.example.authservice.configuration;

import com.example.authservice.aop.RequestLog;
import com.example.authservice.repository.UserRepository;
import com.example.authservice.support.AcmeFixture;
import com.example.authservice.support.AcmeFixture.Acme;
import com.example.authservice.support.AuthServiceIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.regex.Pattern;

import static com.example.authservice.support.ApiCalls.bearer;
import static com.example.authservice.support.ApiCalls.tokenOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * US-8 AC-8.1 (phase 4, T14, E78, FU-14): with the dev profile's logging settings, SQL statements go through the
 * logging system (logger {@code org.hibernate.SQL}), so they carry the request ID and the user ID, show {@code ?}
 * placeholders only, and nothing is printed around the log pattern. The dev profile is active, but the connection
 * still comes from the test database ({@code @ServiceConnection} takes precedence over the dev URL), so this class
 * has its own context. Statements are formatted ({@code format_sql}), so one SQL entry spans several lines; its
 * continuation lines are indented and belong to that entry. The caller's authentication lookup runs before the user
 * is known, so it alone carries {@code userId=-}.
 */
@AuthServiceIntegrationTest
@ActiveProfiles("dev")
@ExtendWith(OutputCaptureExtension.class)
class DevSqlLoggingTest {

    private static final String SQL_LOGGER = "org.hibernate.SQL";
    private static final Pattern ENTRY = Pattern.compile("^\\S+\\s+(TRACE|DEBUG|INFO|WARN|ERROR)\\s.*");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AcmeFixture fixture;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LoggingSystem loggingSystem;

    @Autowired
    private Environment environment;

    private Acme acme;
    private LogLevel sqlLevelFound;

    @BeforeEach
    void seedAcmeAndApplyTheDevSqlLevel() {
        acme = fixture.seed();
        // Logging levels are global in the test JVM, and every context that starts later applies its own (W3), so
        // this context's dev level is re-applied here and the level found is restored afterwards.
        sqlLevelFound = loggingSystem.getLoggerConfiguration(SQL_LOGGER).getConfiguredLevel();
        loggingSystem.setLogLevel(SQL_LOGGER,
                LogLevel.valueOf(environment.getRequiredProperty("logging.level." + SQL_LOGGER)));
    }

    @AfterEach
    void restoreTheSqlLevel() {
        loggingSystem.setLogLevel(SQL_LOGGER, sqlLevelFound);
    }

    @Test
    @Tag("AC-8.1")
    @DisplayName("AC-8.1 dev SQL goes through org.hibernate.SQL with the request and user ids, and without values")
    void sqlLinesGoThroughTheLoggingSystemWithCorrelationAndWithoutBoundValues(CapturedOutput output)
            throws Exception {
        // Given
        String token = tokenOf(mockMvc, "ana@example.com");
        String passwordHash = userRepository.findById(acme.ana().getId()).orElseThrow().getPasswordHash();
        int logStart = output.getAll().length();

        // When
        int status = mockMvc.perform(get("/api/orgs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .header("X-Request-Id", "req-81"))
                .andReturn().getResponse().getStatus();

        // Then
        assertThat(status).isEqualTo(200);
        RequestLog log = RequestLog.since(output, logStart);
        List<String> sqlEntries = log.entries().stream().filter(line -> line.contains(" org.hibernate.SQL ")).toList();
        assertThat(sqlEntries).as("org.hibernate.SQL entries").hasSizeGreaterThan(1)
                .allSatisfy(entry -> assertThat(entry).contains("requestId=req-81"));
        // The first statement is the authentication lookup of the caller, which runs before the user is known: it
        // carries userId=- (reported at T14). Every later statement carries Ana's id.
        assertThat(sqlEntries.get(0)).contains("userId=-");
        assertThat(statement(log.lines(), sqlEntries.get(0))).contains(" from users u1_0 where u1_0.id=?");
        assertThat(sqlEntries.subList(1, sqlEntries.size()))
                .allSatisfy(entry -> assertThat(entry).contains("userId=" + acme.ana().getId()));
        boolean inSqlEntry = false;
        for (String line : log.lines()) {
            if (ENTRY.matcher(line).matches()) {
                inSqlEntry = line.contains(" org.hibernate.SQL ");
                continue;
            }
            assertThat(inSqlEntry && Character.isWhitespace(line.charAt(0)))
                    .as("a line outside the log pattern: " + line).isTrue();
        }
        assertThat(log.lines()).noneMatch(line -> line.startsWith("Hibernate:"))
                .noneMatch(line -> line.contains("ana@example.com") || line.contains("Ana Lima")
                        || line.contains(passwordHash));
    }

    /** The SQL text of an entry: its continuation lines, joined with single spaces. */
    private static String statement(List<String> lines, String entry) {
        StringBuilder sql = new StringBuilder();
        for (int i = lines.indexOf(entry) + 1; i < lines.size() && !ENTRY.matcher(lines.get(i)).matches(); i++) {
            sql.append(' ').append(lines.get(i).trim());
        }
        return sql.toString();
    }
}
