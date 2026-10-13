package com.example.authservice.aop;

import com.example.authservice.aop.RequestLog.AspectLine;
import com.example.authservice.repository.OrgMembershipRepository;
import com.example.authservice.support.AcmeFixture;
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
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static com.example.authservice.support.ApiCalls.bearer;
import static com.example.authservice.support.ApiCalls.tokenOf;
import static com.example.authservice.support.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * AC-9.4: an unexpected failure inside an advised service is logged once at ERROR by the exception handler, and the
 * aspect adds only DEBUG lines naming the exception type. The repository the real organization service calls is
 * stubbed, so the exception passes through the advised service (a mocked service bean isn't advised). This class has
 * its own context, so it uses its own H2 database.
 */
@AuthServiceIntegrationTest
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:unexpected-failure-logging-test;DB_CLOSE_DELAY=-1")
@ExtendWith(OutputCaptureExtension.class)
class UnexpectedFailureLoggingTest {

    private static final String ASPECT_LOGGER = "com.example.authservice.aop";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AcmeFixture fixture;

    @Autowired
    private LoggingSystem loggingSystem;

    @MockitoBean
    private OrgMembershipRepository orgMembershipRepository;

    @BeforeEach
    void seedAcme() {
        fixture.seed();
    }

    @AfterEach
    void restoreLevels() {
        loggingSystem.setLogLevel(ASPECT_LOGGER, null);
    }

    @Test
    @Tag("AC-9.4")
    @DisplayName("AC-9.4 An unexpected failure is not logged twice")
    void anUnexpectedFailureIsNotLoggedTwice(CapturedOutput output) throws Exception {
        // Given
        String token = tokenOf(mockMvc, "ana@example.com");
        loggingSystem.setLogLevel(ASPECT_LOGGER, LogLevel.DEBUG);
        doThrow(new IllegalStateException("boom-QA"))
                .when(orgMembershipRepository).findByUserIdOrderByOrganizationNameAscOrganizationIdAsc(any());
        int start = output.getAll().length();

        // When
        MvcResult result = mockMvc.perform(get("/api/orgs").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andReturn();

        // Then
        assertProblem(result, 500, "INTERNAL_ERROR", "/api/orgs");
        RequestLog log = RequestLog.since(output, start);
        assertThat(log.count("ERROR")).as("ERROR entries").isEqualTo(1);
        assertThat(log.entries()).filteredOn(line -> line.matches("^\\S+\\s+ERROR\\s.*"))
                .singleElement().asString().contains("ApiExceptionHandler");
        List<AspectLine> aspectLines = log.aspectLines();
        assertThat(aspectLines).as("aspect lines").isNotEmpty()
                .allSatisfy(line -> assertThat(line.level()).isEqualTo("DEBUG"))
                .allSatisfy(line -> assertThat(line.line()).doesNotContain("boom-QA"));
        assertThat(aspectLines).anySatisfy(line -> assertThat(line.message()).matches(
                "Leaving OrganizationServiceImpl\\.findByUser: threw IllegalStateException in \\d+ ms"));
        assertThat(log.anAspectLineIsFollowedByAStackTrace()).as("a stack trace after an aspect line").isFalse();
    }
}
