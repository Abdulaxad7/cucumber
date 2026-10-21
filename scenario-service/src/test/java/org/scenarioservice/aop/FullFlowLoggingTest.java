package org.scenarioservice.aop;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.scenarioservice.support.AccessCheckStub;
import org.scenarioservice.support.FeatureData;
import org.scenarioservice.support.GherkinSources;
import org.scenarioservice.support.ScenarioServiceIntegrationTest;
import org.scenarioservice.support.TestSettings;
import org.scenarioservice.support.TestTokens;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggerConfiguration;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.scenarioservice.support.FeatureCalls.create;
import static org.scenarioservice.support.FeatureCalls.features;
import static org.scenarioservice.support.FeatureCalls.fields;
import static org.scenarioservice.support.FeatureCalls.perform;
import static org.scenarioservice.support.FeatureCalls.save;
import static org.scenarioservice.support.ProblemAssertions.body;
import static org.scenarioservice.support.ScenarioFixture.CHECKOUT;
import static org.scenarioservice.support.ScenarioFixture.GUS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * AC-8.9 (D17, T12): with {@code org.scenarioservice} at DEBUG, a full flow over every endpoint logs no content,
 * personal data or secret. The check covers the application's log lines: MockMvc's own print of a failed request is
 * excluded (review point A, deviation 7), and this test only reads entries written by loggers.
 */
@ScenarioServiceIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class FullFlowLoggingTest {

    private static final String SERVICE_LOGGER = "org.scenarioservice";
    private static final String PATH = "features/checkout/payment.feature";

    private final AccessCheckStub stub = AccessCheckStub.get();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LoggingSystem loggingSystem;

    @Autowired
    private FeatureData data;

    @BeforeEach
    void reset() {
        data.reset();
        stub.reset();
    }

    @Test
    @Tag("AC-8.9")
    @DisplayName("AC-8.9 No content, personal data or secrets are logged during a full flow")
    void noContentOrSecretIsLogged(CapturedOutput output) throws Exception {
        List<String> parseErrorMessages = new ArrayList<>();
        String expired = TestTokens.expired(GUS.id(), GUS.email());
        LoggerConfiguration found = loggingSystem.getLoggerConfiguration(SERVICE_LOGGER);
        LogLevel foundLevel = found == null ? null : found.getConfiguredLevel();
        int logStart = output.getAll().length();
        try {
            loggingSystem.setLogLevel(SERVICE_LOGGER, LogLevel.DEBUG);
            MvcResult created = create(mockMvc, GUS, CHECKOUT, fields("path", PATH, "source", GherkinSources.PAYMENT,
                    "message", "First draft"));
            assertThat(created.getResponse().getStatus()).isEqualTo(201);
            UUID payment = UUID.fromString(JsonPath.read(body(created), "$.id"));
            assertThat(save(mockMvc, GUS, CHECKOUT, payment, fields("path", PATH, "source",
                    GherkinSources.PAYMENT_V2, "baseVersion", 1)).getResponse().getStatus()).isEqualTo(200);
            MvcResult invalid = save(mockMvc, GUS, CHECKOUT, payment, fields("path", PATH, "source",
                    GherkinSources.INCONSISTENT_CELLS, "baseVersion", 2));
            assertThat(invalid.getResponse().getStatus()).isEqualTo(422);
            parseErrorMessages.addAll(JsonPath.read(body(invalid), "$.parseErrors[*].message"));
            String feature = features(CHECKOUT) + "/" + payment;
            assertThat(perform(mockMvc, GUS, get(feature + "/test-cases")).getResponse().getStatus()).isEqualTo(200);
            assertThat(perform(mockMvc, GUS, get("/api/projects/" + CHECKOUT + "/tags")).getResponse().getStatus())
                    .isEqualTo(200);
            assertThat(perform(mockMvc, GUS, post(feature + "/archive")).getResponse().getStatus()).isEqualTo(200);
            assertThat(perform(mockMvc, GUS, post(feature + "/restore")).getResponse().getStatus()).isEqualTo(200);
            assertThat(mockMvc.perform(get(features(CHECKOUT)).header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                    .andReturn().getResponse().getStatus()).isEqualTo(401);
            stub.answer(500, "{}");
            assertThat(perform(mockMvc, GUS, get(features(CHECKOUT))).getResponse().getStatus()).isEqualTo(503);
        } finally {
            loggingSystem.setLogLevel(SERVICE_LOGGER, foundLevel);
            stub.reset();
        }

        RequestLog log = RequestLog.since(output, logStart);
        assertThat(log.aspectLines()).as("the flow was logged at DEBUG").isNotEmpty();
        // Every JWT used (so every Authorization value), personal data, content, and the stub's address
        List<String> forbidden = new ArrayList<>(List.of(GUS.token(), expired, "gus@example.com",
                "Customers pay for their basket", "Pay by bank transfer", "Total: 28.00", PATH, "First draft",
                "@regression", "/internal/projects", ":" + TestSettings.ACCESS_CHECK_PORT));
        forbidden.addAll(parseErrorMessages);
        assertThat(parseErrorMessages).isNotEmpty();
        for (String line : log.lines()) {
            String lower = line.toLowerCase(Locale.ROOT);
            for (String value : forbidden) {
                assertThat(lower).as("a log line contains a forbidden value")
                        .doesNotContain(value.toLowerCase(Locale.ROOT));
            }
        }
    }
}
