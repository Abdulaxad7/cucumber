package org.scenarioservice.aop;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.scenarioservice.support.AccessCheckStub;
import org.scenarioservice.support.FeatureCalls;
import org.scenarioservice.support.ScenarioFixture;
import org.scenarioservice.support.ScenarioServiceIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggerConfiguration;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC-8.11 (D17; on the real list endpoint since T8): with {@code app.logging.enabled=false} the aspect logs nothing,
 * even at DEBUG and with a 1 ms slow-call threshold. Its own context (the switch is read at start-up).
 */
@ScenarioServiceIntegrationTest
@TestPropertySource(properties = {"app.logging.enabled=false", "app.logging.slow-call-threshold=1ms"})
@ExtendWith(OutputCaptureExtension.class)
class LoggingSwitchedOffTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LoggingSystem loggingSystem;

    @Test
    @Tag("AC-8.11")
    @DisplayName("AC-8.11 The aspect can be switched off")
    void theAspectCanBeSwitchedOff(CapturedOutput output) throws Exception {
        LoggerConfiguration found = loggingSystem.getLoggerConfiguration(OperationalLoggingTest.ASPECT_LOGGER);
        LogLevel foundLevel = found == null ? null : found.getConfiguredLevel();
        AccessCheckStub.get().reset();
        int logStart = output.getAll().length();
        int status;
        try {
            loggingSystem.setLogLevel(OperationalLoggingTest.ASPECT_LOGGER, LogLevel.DEBUG);
            status = FeatureCalls.list(mockMvc, ScenarioFixture.DAN, ScenarioFixture.CHECKOUT).getResponse()
                    .getStatus();
        } finally {
            loggingSystem.setLogLevel(OperationalLoggingTest.ASPECT_LOGGER, foundLevel);
        }

        assertThat(status).isEqualTo(200);
        assertThat(RequestLog.since(output, logStart).aspectLines()).isEmpty();
    }
}
