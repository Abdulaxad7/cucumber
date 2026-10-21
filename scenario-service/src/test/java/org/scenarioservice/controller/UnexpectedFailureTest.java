package org.scenarioservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.scenarioservice.aop.RequestLog;
import org.scenarioservice.repository.FeatureRepository;
import org.scenarioservice.support.AccessCheckStub;
import org.scenarioservice.support.ScenarioServiceIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggerConfiguration;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.scenarioservice.support.FeatureCalls.list;
import static org.scenarioservice.support.ProblemAssertions.assertProblem;
import static org.scenarioservice.support.ProblemAssertions.body;
import static org.scenarioservice.support.ScenarioFixture.CHECKOUT;
import static org.scenarioservice.support.ScenarioFixture.GUS;

/**
 * AC-7.10 and AC-8.4 (spec note: a repository method the real feature service calls fails, so the exception passes
 * through the advised service). The mocked repository gives this class its own context.
 */
@ScenarioServiceIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class UnexpectedFailureTest {

    private static final String ASPECT_LOGGER = "org.scenarioservice.aop";

    @MockitoBean
    private FeatureRepository features;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LoggingSystem loggingSystem;

    @BeforeEach
    void failTheFeatureList() {
        AccessCheckStub.get().reset();
        given(features.findByProjectIdAndArchivedAtIsNull(any())).willThrow(new IllegalStateException("boom-QA"));
    }

    @Test
    @Tag("AC-7.10")
    @DisplayName("AC-7.10 An unexpected failure reveals nothing")
    void anUnexpectedFailureRevealsNothing() throws Exception {
        MvcResult result = list(mockMvc, GUS, CHECKOUT);

        assertProblem(result, 500, "INTERNAL_ERROR");
        assertThat(body(result)).doesNotContain("boom-QA", "IllegalStateException");
    }

    @Test
    @Tag("AC-8.4")
    @DisplayName("AC-8.4 An unexpected failure is logged exactly once")
    void anUnexpectedFailureIsLoggedExactlyOnce(CapturedOutput output) throws Exception {
        LoggerConfiguration found = loggingSystem.getLoggerConfiguration(ASPECT_LOGGER);
        LogLevel foundLevel = found == null ? null : found.getConfiguredLevel();
        int logStart = output.getAll().length();
        try {
            loggingSystem.setLogLevel(ASPECT_LOGGER, LogLevel.DEBUG);
            assertProblem(list(mockMvc, GUS, CHECKOUT), 500, "INTERNAL_ERROR");
        } finally {
            loggingSystem.setLogLevel(ASPECT_LOGGER, foundLevel);
        }

        RequestLog log = RequestLog.since(output, logStart);
        assertThat(log.count("ERROR")).isEqualTo(1);
        assertThat(log.entries()).filteredOn(line -> line.contains(" ERROR ")).singleElement()
                .satisfies(line -> assertThat(line).contains("ApiExceptionHandler"));
        assertThat(log.aspectLines()).isNotEmpty()
                .anySatisfy(line -> assertThat(line.message()).contains("threw IllegalStateException"))
                .allSatisfy(line -> assertThat(line.line()).doesNotContain("boom-QA"));
        List<String> lines = log.lines();
        List<String> aspectEntries = log.aspectLines().stream().map(RequestLog.AspectLine::line).toList();
        for (int i = 0; i < lines.size() - 1; i++) {
            if (aspectEntries.contains(lines.get(i))) {
                assertThat(lines.get(i + 1)).as("the line after an aspect line").doesNotStartWith("\tat ");
            }
        }
    }
}
