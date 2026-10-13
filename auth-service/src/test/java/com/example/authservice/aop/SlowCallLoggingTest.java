package com.example.authservice.aop;

import com.example.authservice.support.AcmeFixture;
import com.example.authservice.support.AuthServiceIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * AC-9.5: a call slower than {@code app.logging.slow-call-threshold} is logged at WARN, without personal data. Each
 * outline row sets its own threshold, so each has its own context and its own H2 database.
 */
@AuthServiceIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class SlowCallLoggingTest {

    private static final String FAY = "{\"email\":\"fay@example.com\",\"password\":\"Secret123!\",\"fullName\":\"Fay Lowe\"}";

    @Nested
    @TestPropertySource(properties = {
            "app.logging.slow-call-threshold=1ms",
            "spring.datasource.url=jdbc:h2:mem:slow-call-1ms-test;DB_CLOSE_DELAY=-1"})
    class ThresholdOfOneMillisecond {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private AcmeFixture fixture;

        @BeforeEach
        void emptyDatabase() {
            fixture.reset();
        }

        @Test
        @Tag("AC-9.5")
        @DisplayName("AC-9.5 threshold 1ms: the registration is logged once at WARN")
        void aCallSlowerThanTheThresholdIsLoggedAtWarn(CapturedOutput output) throws Exception {
            RequestLog log = registerFay(mockMvc, output);

            assertThat(log.aspectLines()).filteredOn(line -> line.level().equals("WARN")).singleElement()
                    .satisfies(line -> assertThat(line.message()).matches(
                            "Slow call AuthServiceImpl\\.register: returned in \\d+ ms \\(threshold 1 ms\\)"));
        }
    }

    @Nested
    @TestPropertySource(properties = {
            "app.logging.slow-call-threshold=1h",
            "spring.datasource.url=jdbc:h2:mem:slow-call-1h-test;DB_CLOSE_DELAY=-1"})
    class ThresholdOfOneHour {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private AcmeFixture fixture;

        @BeforeEach
        void emptyDatabase() {
            fixture.reset();
        }

        @Test
        @Tag("AC-9.5")
        @DisplayName("AC-9.5 threshold 1h: the registration writes no WARN line")
        void aCallFasterThanTheThresholdIsNotLoggedAtWarn(CapturedOutput output) throws Exception {
            RequestLog log = registerFay(mockMvc, output);

            assertThat(log.aspectLines()).filteredOn(line -> line.level().equals("WARN")).isEmpty();
        }
    }

    private static RequestLog registerFay(MockMvc mockMvc, CapturedOutput output) throws Exception {
        int start = output.getAll().length();

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FAY))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        RequestLog log = RequestLog.since(output, start);
        assertThat(log.lines()).noneMatch(line ->
                line.contains("fay@example.com") || line.contains("Fay Lowe") || line.contains("Secret123!"));
        return log;
    }
}
