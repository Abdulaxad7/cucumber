package com.example.authservice.aop;

import com.example.authservice.aop.RequestLog.AspectLine;
import com.example.authservice.repository.UserRepository;
import com.example.authservice.support.AcmeFixture;
import com.example.authservice.support.AcmeFixture.Acme;
import com.example.authservice.support.AuthServiceIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.example.authservice.support.ApiCalls.bearer;
import static com.example.authservice.support.ApiCalls.loginBody;
import static com.example.authservice.support.ApiCalls.tokenOf;
import static com.example.authservice.support.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * US-9 (D17): the logging aspect on the service layer, end to end through the real filters, security chain and
 * services. Levels are changed through Spring Boot's {@link LoggingSystem} for one test only and restored afterwards,
 * because Logback's levels are shared by every context in the test JVM.
 */
@AuthServiceIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class OperationalLoggingTest {

    private static final String ASPECT_LOGGER = "com.example.authservice.aop";
    private static final String SERVICE_LOGGER = "com.example.authservice";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AcmeFixture fixture;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LoggingSystem loggingSystem;

    private final List<String> changedLoggers = new ArrayList<>();

    private Acme acme;

    @BeforeEach
    void seedAcme() {
        acme = fixture.seed();
    }

    @AfterEach
    void restoreLevels() {
        changedLoggers.forEach(logger -> loggingSystem.setLogLevel(logger, null));
        changedLoggers.clear();
    }

    @Test
    @Tag("AC-9.1")
    @DisplayName("AC-9.1 A use case logs its entry and exit at DEBUG, with a duration and without arguments")
    void aUseCaseLogsItsEntryAndExitAtDebug(CapturedOutput output) throws Exception {
        // Given
        String token = tokenOf(mockMvc, "ana@example.com");
        atDebug(ASPECT_LOGGER);
        int start = output.getAll().length();

        // When
        MvcResult result = mockMvc.perform(post("/api/orgs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .header(REQUEST_ID_HEADER, "req-91")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Logging Org QA\"}"))
                .andReturn();

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        RequestLog log = RequestLog.since(output, start);
        List<AspectLine> aspectLines = log.aspectLines();
        assertThat(aspectLines).as("aspect lines").allSatisfy(line -> assertThat(line.level()).isEqualTo("DEBUG"));
        assertThat(aspectLines).filteredOn(line ->
                        line.message().equals("Entering OrganizationServiceImpl.createOrganization"))
                .as("entry lines").hasSize(1);
        assertThat(aspectLines).filteredOn(line -> line.message().matches(
                        "Leaving OrganizationServiceImpl\\.createOrganization: returned in \\d+ ms"))
                .as("exit lines with a duration").hasSize(1);
        assertThat(log.lines()).noneMatch(line -> line.contains("Logging Org QA"));
    }

    @Test
    @Tag("AC-9.2")
    @DisplayName("AC-9.2 At the default level, a successful use case writes no aspect line")
    void atTheDefaultLevelASuccessfulUseCaseWritesNoAspectLine(CapturedOutput output) throws Exception {
        // Given
        String token = tokenOf(mockMvc, "ana@example.com");
        int start = output.getAll().length();

        // When
        MvcResult result = mockMvc.perform(get("/api/orgs").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andReturn();

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(RequestLog.since(output, start).aspectLines()).isEmpty();
    }

    @Test
    @Tag("AC-9.3")
    @DisplayName("AC-9.3 A business error is logged once at INFO, with its code and without a stack trace")
    void aBusinessErrorIsLoggedOnceAtInfo(CapturedOutput output) throws Exception {
        // Given
        String token = tokenOf(mockMvc, "ana@example.com");
        int start = output.getAll().length();

        // When
        MvcResult result = mockMvc.perform(post(invitations())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dan@example.com\",\"role\":\"MEMBER\"}"))
                .andReturn();

        // Then
        assertProblem(result, 409, "ALREADY_MEMBER", invitations());
        RequestLog log = RequestLog.since(output, start);
        assertThat(log.aspectLines()).singleElement().satisfies(line -> {
            assertThat(line.level()).isEqualTo("INFO");
            assertThat(line.message()).contains("ALREADY_MEMBER");
        });
        assertThat(log.hasStackTraceLine()).as("a stack trace line").isFalse();
        assertThat(log.lines()).noneMatch(line -> line.toLowerCase(Locale.ROOT).contains("dan@example.com"));
    }

    @Test
    @Tag("AC-9.9")
    @DisplayName("AC-9.9 No personal data or secrets are logged during the full account flow")
    void noPersonalDataOrSecretsAreLoggedDuringTheFullAccountFlow(CapturedOutput output) throws Exception {
        // Given
        atDebug(SERVICE_LOGGER);
        int start = output.getAll().length();

        // When: Fay registers, logs in and creates "Fay Org QA"
        assertThat(status(json(post("/api/auth/register"),
                "{\"email\":\"fay@example.com\",\"password\":\"Secret123!\",\"fullName\":\"Fay Lowe\"}")))
                .isEqualTo(201);
        String fayToken = tokenOf(mockMvc, "fay@example.com");
        assertThat(status(json(post("/api/orgs").header(HttpHeaders.AUTHORIZATION, bearer(fayToken)),
                "{\"name\":\"Fay Org QA\"}"))).isEqualTo(201);
        // And Ana invites gus@example.com as MEMBER to Acme
        String anaToken = tokenOf(mockMvc, "ana@example.com");
        MvcResult invited = mockMvc.perform(json(post(invitations())
                        .header(HttpHeaders.AUTHORIZATION, bearer(anaToken)),
                "{\"email\":\"gus@example.com\",\"role\":\"MEMBER\"}")).andReturn();
        assertThat(invited.getResponse().getStatus()).isEqualTo(201);
        String invitationToken = JsonPath.read(invited.getResponse().getContentAsString(), "$.token");
        // And Gus accepts the invitation anonymously
        assertThat(status(json(post("/api/auth/invitations/" + invitationToken + "/accept"),
                "{\"password\":\"Secret123!\",\"fullName\":\"Gus Hale\"}"))).isEqualTo(201);
        // And Fay logs in with a wrong password, and registers again
        assertThat(status(json(post("/api/auth/login"), loginBody("fay@example.com", "Wrong123!"))))
                .isEqualTo(401);
        assertThat(status(json(post("/api/auth/register"),
                "{\"email\":\"fay@example.com\",\"password\":\"Secret123!\",\"fullName\":\"Fay Lowe\"}")))
                .isEqualTo(409);

        // Then
        String log = output.getAll().substring(start);
        List<String> hashes = userRepository.findAll().stream().map(user -> user.getPasswordHash()).toList();
        assertThat(log).doesNotContain("Secret123!", "Wrong123!", "$2a$", "$2b$", "$2y$");
        assertThat(log).doesNotContain(hashes.toArray(String[]::new));
        assertThat(log).doesNotContain(fayToken, anaToken, "Bearer ", "eyJ", invitationToken);
        assertThat(log.toLowerCase(Locale.ROOT))
                .doesNotContain("fay@example.com", "gus@example.com", "ana@example.com", "fay lowe", "gus hale");
    }

    @RepeatedTest(value = 5, name = "AC-9.10 A registration race doesn't log the email (run {currentRepetition})")
    @Tag("AC-9.10")
    void aRegistrationRaceDoesNotLogTheEmail(CapturedOutput output) throws Exception {
        // Given
        fixture.reset();
        int start = output.getAll().length();

        // When
        List<MvcResult> results = registerSimultaneously(
                "{\"email\":\"race-qa@example.com\",\"password\":\"Secret123!\",\"fullName\":\"Race QA\"}", 5);

        // Then
        assertThat(results).filteredOn(result -> result.getResponse().getStatus() == 201).hasSize(1);
        for (MvcResult loser : results.stream().filter(result -> result.getResponse().getStatus() != 201).toList()) {
            assertProblem(loser, 409, "EMAIL_ALREADY_REGISTERED", "/api/auth/register");
        }
        assertThat(output.getAll().substring(start).toLowerCase(Locale.ROOT)).doesNotContain("race-qa@example.com");
    }

    @Test
    @Tag("AC-9.11")
    @DisplayName("AC-9.11 The JWT filter path is not advised")
    void theJwtFilterPathIsNotAdvised(CapturedOutput output) throws Exception {
        // Given
        String token = tokenOf(mockMvc, "ana@example.com");
        atDebug(ASPECT_LOGGER);
        int start = output.getAll().length();

        // When
        MvcResult result = mockMvc.perform(get("/api/orgs").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andReturn();

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        List<AspectLine> aspectLines = RequestLog.since(output, start).aspectLines();
        assertThat(aspectLines).as("the service call is advised")
                .anyMatch(line -> line.message().startsWith("Entering OrganizationServiceImpl.findByUser"));
        assertThat(aspectLines).noneMatch(line ->
                line.message().contains("CustomUserDetailsService") || line.message().contains("JwtTokenProvider"));
    }

    private void atDebug(String logger) {
        loggingSystem.setLogLevel(logger, LogLevel.DEBUG);
        changedLoggers.add(logger);
    }

    private String invitations() {
        return "/api/orgs/" + acme.orgId() + "/invitations";
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private int status(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }

    private List<MvcResult> registerSimultaneously(String body, int callers) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return mockMvc.perform(json(post("/api/auth/register"), body)).andReturn();
                }));
            }
            start.countDown();
            List<MvcResult> results = new ArrayList<>();
            for (Future<MvcResult> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
