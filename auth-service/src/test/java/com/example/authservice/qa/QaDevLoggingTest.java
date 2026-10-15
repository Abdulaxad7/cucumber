package com.example.authservice.qa;

import com.example.authservice.repository.UserRepository;
import com.example.authservice.support.AuthServiceIntegrationTest;
import com.example.authservice.support.AcmeFixture;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggerConfiguration;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static com.example.authservice.support.ApiCalls.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * qa, phase 4 US-8 and D14 (adversarial): with the dev profile's logging settings, a whole account flow that writes
 * personal data and secrets (register, log in, create an organization, invite, accept anonymously, log in again, and
 * a registration that a database constraint rejects) logs its SQL through {@code org.hibernate.SQL} with the request
 * id, and no line contains an email, a full name, a password, a password hash, an invitation token, an access token,
 * the JWT secret this context signs with, a bound value or PostgreSQL's error DETAIL. The context is the one
 * of {@code configuration/DevSqlLoggingTest} (the dev profile on the shared test database).
 * <p>
 * Log levels live in the one Logback configuration of the test JVM, and every test context that starts later
 * re-applies its own levels. So before the flow, this test re-applies the level that this context's dev profile
 * configures for {@code org.hibernate.SQL} (read from the context's environment), and afterwards restores the level
 * it found.
 */
@AuthServiceIntegrationTest
@ActiveProfiles("dev")
@ExtendWith(OutputCaptureExtension.class)
class QaDevLoggingTest {

    private static final String PASSWORD = "Dev-Secret-QA-61!";

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

    private LogLevel sqlLevelFound;

    @BeforeEach
    void seedAcmeAndApplyTheDevSqlLevel() {
        fixture.seed();
        LoggerConfiguration sql = loggingSystem.getLoggerConfiguration("org.hibernate.SQL");
        sqlLevelFound = sql == null ? null : sql.getConfiguredLevel();
        loggingSystem.setLogLevel("org.hibernate.SQL",
                LogLevel.valueOf(environment.getRequiredProperty("logging.level.org.hibernate.SQL")));
    }

    @AfterEach
    void restoreTheSqlLevel() {
        loggingSystem.setLogLevel("org.hibernate.SQL", sqlLevelFound);
    }

    @Test
    @Tag("AC-8.1")
    @DisplayName("qa AC-8.1/D14: a full account flow in dev logs correlated SQL and no personal data or secret")
    void aFullAccountFlowLogsNoPersonalDataOrSecret(CapturedOutput output) throws Exception {
        // Given
        int logStart = output.getAll().length();
        List<String> secrets = new ArrayList<>(List.of("fay-dev-qa@example.com", "Fay DevQA", "kit-dev-qa@example.com",
                "Kit DevQA", PASSWORD, "\uA7C0dev-qa@example.com", environment.getRequiredProperty("app.jwtSecret")));

        // When
        int registered = perform(json(post("/api/auth/register"),
                "{\"email\":\"Fay-Dev-QA@Example.com\",\"password\":\"" + PASSWORD + "\",\"fullName\":\"Fay DevQA\"}"))
                .getResponse().getStatus();
        String fay = login("fay-dev-qa@example.com");
        MvcResult org = perform(json(post("/api/orgs"), "{\"name\":\"Fay DevQA Org\"}").header("X-Request-Id",
                "req-qa-dev").header(HttpHeaders.AUTHORIZATION, bearer(fay)));
        String orgId = JsonPath.read(org.getResponse().getContentAsString(), "$.id");
        MvcResult invited = perform(json(post("/api/orgs/" + orgId + "/invitations"),
                "{\"email\":\"kit-dev-qa@example.com\",\"role\":\"MEMBER\"}")
                .header(HttpHeaders.AUTHORIZATION, bearer(fay)));
        String invitationToken = JsonPath.read(invited.getResponse().getContentAsString(), "$.token");
        int accepted = perform(json(post("/api/auth/invitations/" + invitationToken + "/accept"),
                "{\"password\":\"" + PASSWORD + "\",\"fullName\":\"Kit DevQA\"}")).getResponse().getStatus();
        String kit = login("kit-dev-qa@example.com");
        int listed = perform(get("/api/orgs").header(HttpHeaders.AUTHORIZATION, bearer(kit))).getResponse().getStatus();
        // A value the database itself rejects (lower-case check), so a constraint error with the row is raised
        int rejected = perform(json(post("/api/auth/register"),
                "{\"email\":\"\uA7C0dev-qa@example.com\",\"password\":\"" + PASSWORD + "\",\"fullName\":\"Fay DevQA\"}"))
                .getResponse().getStatus();

        // Then (the log ends here; the hash lookup below runs outside any request)
        List<String> lines = List.of(output.getAll().substring(logStart).split("\\R"));
        assertThat(List.of(registered, org.getResponse().getStatus(), invited.getResponse().getStatus(), accepted,
                listed)).containsExactly(201, 201, 201, 201, 200);
        assertThat(rejected).as("never a server error").isLessThan(500);
        secrets.add(invitationToken);
        secrets.add(fay);
        secrets.add(kit);
        userRepository.findAll().stream().map(user -> user.getPasswordHash()).filter(Objects::nonNull)
                .forEach(secrets::add);

        assertThat(lines).filteredOn(line -> line.contains(" org.hibernate.SQL ")).isNotEmpty()
                .allSatisfy(line -> assertThat(line).containsPattern("requestId=[^- \\]]"));
        assertThat(lines).filteredOn(line -> line.contains("req-qa-dev") && line.contains(" org.hibernate.SQL "))
                .isNotEmpty();
        for (String line : lines) {
            String lower = line.toLowerCase(Locale.ROOT);
            for (String secret : secrets) {
                assertThat(lower).as("a log line contains a secret or personal value")
                        .doesNotContain(secret.toLowerCase(Locale.ROOT));
            }
            assertThat(lower).doesNotContain("failing row", "key (", "binding parameter", "org.hibernate.orm.jdbc.bind",
                    "hibernate: ");
        }
    }

    private MvcResult perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn();
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String login(String email) throws Exception {
        MvcResult login = perform(json(post("/api/auth/login"),
                "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"));
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        return JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
    }
}
