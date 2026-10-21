package org.scenarioservice.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.scenarioservice.support.AccessCheckStub;
import org.scenarioservice.support.ScenarioServiceIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.scenarioservice.support.FeatureCalls.features;
import static org.scenarioservice.support.ProblemAssertions.assertProblem;
import static org.scenarioservice.support.ProblemAssertions.body;
import static org.scenarioservice.support.ScenarioFixture.CHECKOUT;
import static org.scenarioservice.support.ScenarioFixture.GUS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * AC-7.9 (D16, end to end since T8), 406, the request-id echo rule, and P17 (database conflicts are 409 CONFLICT,
 * driven through the test-only probe, which can raise them on demand). AC-7.10 is in
 * {@code controller/UnexpectedFailureTest}.
 */
@ScenarioServiceIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class ProblemDetailsTest {

    private static final String VALID_BODY = "{\"path\":\"features/a.feature\",\"source\":\"Feature: A\\n\"}";

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void resetTheStub() {
        AccessCheckStub.get().reset();
    }

    @ParameterizedTest(name = "AC-7.9 {0}")
    @ValueSource(strings = {"unknown path", "DELETE", "text/plain", "unreadable JSON", "archived=maybe",
            "source 123"})
    @Tag("AC-7.9")
    @DisplayName("AC-7.9 Every error is a Problem with a stable code")
    void everyErrorIsAProblemWithAStableCode(String row) throws Exception {
        MockHttpServletRequestBuilder request = switch (row) {
            case "unknown path" -> get("/api/projects/" + CHECKOUT + "/unknown");
            case "DELETE" -> delete(features(CHECKOUT) + "/" + UUID.randomUUID());
            case "text/plain" -> post(features(CHECKOUT)).contentType(MediaType.TEXT_PLAIN).content(VALID_BODY);
            case "unreadable JSON" -> post(features(CHECKOUT)).contentType(MediaType.APPLICATION_JSON)
                    .content("{not json");
            case "archived=maybe" -> get(features(CHECKOUT)).queryParam("archived", "maybe");
            default -> post(features(CHECKOUT)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"path\":\"features/a.feature\",\"source\":123}");
        };
        int status = switch (row) {
            case "unknown path" -> 404;
            case "DELETE" -> 405;
            case "text/plain" -> 415;
            default -> 400;
        };
        String code = switch (row) {
            case "unknown path" -> "NOT_FOUND";
            case "DELETE" -> "METHOD_NOT_ALLOWED";
            case "text/plain" -> "UNSUPPORTED_MEDIA_TYPE";
            default -> "MALFORMED_REQUEST";
        };

        MvcResult result = mockMvc.perform(authenticated(request).header("X-Request-Id", "req-79")).andReturn();

        assertThat(assertProblem(result, status, code)).containsEntry("requestId", "req-79");
    }

    @ParameterizedTest(name = "P17 the failure \"{0}\" is 409 CONFLICT")
    @ValueSource(strings = {"unique", "lock", "deadlock"})
    @DisplayName("P17, D14 item 12: a unique violation, a lock timeout or a deadlock is 409 CONFLICT, never a 500")
    void databaseConflictsAreConflicts(String kind, CapturedOutput output) throws Exception {
        int logStart = output.getAll().length();

        MvcResult result = mockMvc.perform(authenticated(get("/api/projects/" + CHECKOUT + "/probe/failures/"
                + kind))).andReturn();

        assertProblem(result, 409, "CONFLICT");
        String log = output.getAll().substring(logStart);
        assertThat(body(result) + log).doesNotContain("uk_features", "secret-path-QA", "lock timeout QA",
                "deadlock detected QA");
        assertThat(log).contains(" WARN ").doesNotContain(" ERROR ");
    }

    @Test
    @DisplayName("D16: 406 for an Accept header that excludes every response type (the Problem body is optional then)")
    void notAcceptable() throws Exception {
        MvcResult result = mockMvc.perform(authenticated(get(features(CHECKOUT))).accept(MediaType.TEXT_PLAIN))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(406);
    }

    @ParameterizedTest(name = "D16: the request id \"{0}\" is not echoed")
    @ValueSource(strings = {"bad id QA", "x/y"})
    @DisplayName("D16: an invalid request id is not echoed")
    void anInvalidRequestIdIsNotEchoed(String requestId) throws Exception {
        MvcResult result = mockMvc.perform(authenticated(get("/api/projects/" + CHECKOUT + "/unknown"))
                .header("X-Request-Id", requestId)).andReturn();

        Map<String, Object> problem = assertProblem(result, 404, "NOT_FOUND");
        assertThat(problem).doesNotContainKey("requestId");
    }

    private static MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, GUS.bearer());
    }
}
