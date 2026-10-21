package com.example.authservice.aop;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static com.example.authservice.support.ApiCalls.bearer;
import static com.example.authservice.support.ApiCalls.tokenOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * AC-9.12: with {@code app.logging.enabled=false} the aspect logs nothing, even at DEBUG.
 */
@AuthServiceIntegrationTest
@TestPropertySource(properties = {
        "app.logging.enabled=false"})
@ExtendWith(OutputCaptureExtension.class)
class LoggingSwitchedOffTest {

    private static final String ASPECT_LOGGER = "com.example.authservice.aop";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AcmeFixture fixture;

    @Autowired
    private LoggingSystem loggingSystem;

    @BeforeEach
    void seedAcme() {
        fixture.seed();
    }

    @AfterEach
    void restoreLevels() {
        loggingSystem.setLogLevel(ASPECT_LOGGER, null);
    }

    @Test
    @Tag("AC-9.12")
    @DisplayName("AC-9.12 The aspect can be switched off")
    void theAspectCanBeSwitchedOff(CapturedOutput output) throws Exception {
        // Given
        String token = tokenOf(mockMvc, "ana@example.com");
        loggingSystem.setLogLevel(ASPECT_LOGGER, LogLevel.DEBUG);
        int start = output.getAll().length();

        // When
        MvcResult result = mockMvc.perform(get("/api/orgs").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andReturn();

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(RequestLog.since(output, start).aspectLines()).isEmpty();
    }
}
