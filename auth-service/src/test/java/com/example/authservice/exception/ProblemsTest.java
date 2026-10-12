package com.example.authservice.exception;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests of the Problem members (D2), including the request ID rule (AC-1.7).
 */
class ProblemsTest {

    @Test
    void aProblemHasTheContractMembers() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orgs");
        request.setQueryString("x=1");

        // When
        ProblemDetail problem = Problems.of(ErrorCode.SEAT_LIMIT_REACHED, "No free seat.", request);

        // Then
        assertThat(problem.getType()).isEqualTo(URI.create("about:blank"));
        assertThat(problem.getTitle()).isEqualTo("Conflict");
        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getDetail()).isEqualTo("No free seat.");
        assertThat(problem.getInstance()).isEqualTo(URI.create("/api/orgs"));
        assertThat(problem.getProperties()).containsEntry("code", "SEAT_LIMIT_REACHED").doesNotContainKey("requestId");
    }

    @ParameterizedTest(name = "valid X-Request-Id \"{0}\"")
    @ValueSource(strings = {"rid-123", "a", "A.b_c-9"})
    @Tag("AC-1.7")
    void aValidRequestIdIsEchoed(String requestId) {
        assertThat(Problems.of(ErrorCode.CONFLICT, "d", requestWithId(requestId)).getProperties())
                .containsEntry("requestId", requestId);
    }

    @Test
    @Tag("AC-1.7")
    void aRequestIdOf128CharactersIsEchoedAndOneOf129IsNot() {
        String longest = "r".repeat(128);
        assertThat(Problems.of(ErrorCode.CONFLICT, "d", requestWithId(longest)).getProperties())
                .containsEntry("requestId", longest);
        assertThat(Problems.of(ErrorCode.CONFLICT, "d", requestWithId(longest + "r")).getProperties())
                .doesNotContainKey("requestId");
    }

    @ParameterizedTest(name = "invalid X-Request-Id \"{0}\"")
    @ValueSource(strings = {"", "bad id!", "ridé", "rid/1", "<script>"})
    @Tag("AC-1.7")
    void anInvalidRequestIdIsNotEchoed(String requestId) {
        assertThat(Problems.of(ErrorCode.CONFLICT, "d", requestWithId(requestId)).getProperties())
                .doesNotContainKey("requestId");
    }

    @Test
    void everyCodeHasAnErrorStatus() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.status().isError()).as(code.name()).isTrue();
        }
    }

    private static MockHttpServletRequest requestWithId(String requestId) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");
        request.addHeader("X-Request-Id", requestId);
        return request;
    }
}
