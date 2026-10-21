package org.apigateway.qa;

import org.apigateway.support.GatewayIntegrationTest;
import org.apigateway.support.RecordedRequest;
import org.apigateway.support.TestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.List;
import java.util.stream.Stream;

import static org.apigateway.qa.QaSupport.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * QA re-verification of T18 (D4, AC-2.5, AC-2.9, AC-2.10): ambiguous credentials and unsafe subjects beyond the
 * spec's rows. Requests go over a raw socket, so duplicate header lines and header-name casing arrive exactly as
 * written.
 */
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("QA: duplicate Authorization headers and unsafe token subjects (fix round)")
class QaCredentialAmbiguityTest extends GatewayIntegrationTest {

    @LocalServerPort
    private int port;

    static Stream<Arguments> ambiguousAuthorizationHeaders() {
        String valid = TestTokens.bearerValid();
        return Stream.of(
                Arguments.of("same token twice, the second header name in lower case",
                        List.of("Authorization: " + valid, "authorization: " + valid)),
                Arguments.of("a valid token and an empty Authorization header",
                        List.of("Authorization: " + valid, "Authorization: ")),
                Arguments.of("an empty Authorization header and then a valid token",
                        List.of("Authorization: ", "Authorization: " + valid)),
                Arguments.of("a valid token and Basic credentials",
                        List.of("Authorization: " + valid, "Authorization: Basic YW5hOlNlY3JldDEyMyE=")),
                Arguments.of("three copies of the valid token",
                        List.of("Authorization: " + valid, "AUTHORIZATION: " + valid, "Authorization: " + valid)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("ambiguousAuthorizationHeaders")
    @Tag("AC-2.9")
    @DisplayName("AC-2.9 (more rows): a protected request with more than one Authorization header line gets the 401 Problem and reaches no service")
    void moreThanOneAuthorizationHeaderLineIsRejected(String description, List<String> headerLines) {
        // When
        RawHttp.Response response = RawHttp.send(port, "POST", "/api/orgs", headerLines.toArray(String[]::new));

        // Then
        assertProblem(response.status(), response.headerValues("Content-Type").get(0),
                response.headerValues("X-Request-Id").get(0), response.body(), 401, "Unauthorized", "/api/orgs");
        assertThat(response.headerValues("WWW-Authenticate")).singleElement()
                .satisfies(challenge -> assertThat(challenge).startsWith("Bearer"));
        assertNoStubReceivedARequest();
    }

    @Test
    @Tag("AC-2.5")
    @DisplayName("AC-2.5 / D4: a public operation with two Authorization headers is forwarded, both headers unchanged and in order")
    void publicOperationsForwardEveryAuthorizationHeaderUnevaluated() {
        // Given
        String valid = TestTokens.bearerValid();
        String expired = "Bearer " + TestTokens.expired();

        // When
        RawHttp.Response login = RawHttp.send(port, "POST", "/api/auth/login",
                "Authorization: " + valid, "Authorization: Bearer not-a-jwt");
        RawHttp.Response register = RawHttp.send(port, "POST", "/api/auth/register",
                "Authorization: " + expired, "Authorization: Bearer not-a-jwt");

        // Then
        assertThat(login.status()).isEqualTo(200);
        assertThat(register.status()).isEqualTo(200);
        assertThat(AUTH_SERVICE.requests()).hasSize(2);
        RecordedRequest forwardedLogin = AUTH_SERVICE.requests().get(0);
        RecordedRequest forwardedRegister = AUTH_SERVICE.requests().get(1);
        assertThat(forwardedLogin.headerValues("Authorization")).containsExactly(valid, "Bearer not-a-jwt");
        assertThat(forwardedRegister.headerValues("Authorization")).containsExactly(expired, "Bearer not-a-jwt");
        assertThat(forwardedLogin.headerValues("X-User-Id")).isEmpty();
        assertThat(forwardedRegister.headerValues("X-User-Id")).isEmpty();
    }

    @Test
    @Tag("AC-2.2")
    @DisplayName("D4 (unchanged by T18): one Authorization header with the scheme in lower case is accepted and forwarded byte for byte")
    void aSingleLowerCaseSchemeIsStillAccepted() {
        // Given
        String authorization = "bearer " + TestTokens.valid();

        // When
        RawHttp.Response response = RawHttp.send(port, "POST", "/api/orgs", "authorization: " + authorization);

        // Then
        assertThat(response.status()).isEqualTo(200);
        assertThat(AUTH_SERVICE.requests()).singleElement().satisfies(forwarded -> {
            assertThat(forwarded.headerValues("Authorization")).containsExactly(authorization);
            assertThat(forwarded.headerValues("X-User-Id")).containsExactly(TestTokens.USER_ID);
        });
    }

    static Stream<Arguments> unsafeSubjects() {
        return Stream.of(
                Arguments.of(1, "a tab", "\"abc\\tdef\""),
                Arguments.of(2, "DEL (U+007F)", "\"abc\\u007f\""),
                Arguments.of(3, "NUL (U+0000)", "\"abc\\u0000\""),
                Arguments.of(4, "a bare CR", "\"abc\\rX-Injected: 1\""),
                Arguments.of(5, "a non-breaking space (U+00A0)", "\"abc\\u00a0def\""),
                Arguments.of(6, "a number instead of a string", "123"),
                Arguments.of(7, "an array", "[\"a\",\"b\"]"),
                Arguments.of(8, "an object", "{\"id\":\"a\"}"),
                Arguments.of(9, "a boolean", "true"),
                Arguments.of(10, "JSON null", "null"));
    }

    @ParameterizedTest(name = "sub-qa-{0}: {1}")
    @MethodSource("unsafeSubjects")
    @Tag("AC-2.10")
    @DisplayName("AC-2.10 (more rows): a signed token whose sub is not printable ASCII text gets 401, one single-line access entry without userId, and no 500")
    void unsafeOrNonStringSubjectsAreRejected(int n, String description, String subJson, CapturedOutput output) {
        // Given a token correctly signed with the shared secret, expiring in 1 hour
        String token = QaSupport.hs512Signed(QaSupport.claimsWithRawSubject(subJson));
        String requestId = "sub-qa-" + n;

        // When
        RawHttp.Response response = RawHttp.send(port, "POST", "/api/orgs",
                "Authorization: Bearer " + token, "X-Request-Id: " + requestId);

        // Then
        assertThat(response.status()).isEqualTo(401);
        assertNoStubReceivedARequest();
        String entry = QaSupport.onlyAccessEntry(output, requestId);
        assertThat(entry).contains(" status=401 ").doesNotContain(" userId=");
        assertThat(output.getAll().lines().filter(line -> line.contains("requestId=" + requestId + " ")).count())
                .as("lines carrying the request ID (the entry is a single line)").isEqualTo(1);
        assertThat(output.getAll()).doesNotContain("Unexpected gateway error", "X-Injected");
    }

    @ParameterizedTest(name = "sub {0}")
    @MethodSource("printableEdgeSubjects")
    @Tag("AC-3.1")
    @DisplayName("AC-3.1 / D4: printable-ASCII subjects at the edge of the rule are accepted and forwarded unchanged in X-User-Id")
    void printableAsciiSubjectsAreAccepted(String subject) {
        // When
        RawHttp.Response response = RawHttp.send(port, "POST", "/api/orgs",
                "Authorization: Bearer " + QaSupport.hs512WithSubject(subject));

        // Then
        assertThat(response.status()).isEqualTo(200);
        assertThat(AUTH_SERVICE.requests()).singleElement().satisfies(forwarded ->
                assertThat(forwarded.headerValues("X-User-Id")).containsExactly(subject));
    }

    static Stream<String> printableEdgeSubjects() {
        return Stream.of("!", "~", "a\"b\\c", "abc=forged", "x".repeat(512));
    }
}
