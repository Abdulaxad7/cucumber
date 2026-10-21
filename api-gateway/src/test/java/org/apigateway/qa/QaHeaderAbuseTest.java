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

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QA adversarial checks for untrusted headers (D4, D7, P6): X-User-Id spoofing and X-Request-Id injection.
 * Raw sockets are used so that duplicate headers, header-name casing and non-ASCII bytes arrive as written.
 */
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("QA: X-User-Id spoofing and X-Request-Id injection")
class QaHeaderAbuseTest extends GatewayIntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    @Tag("AC-3.2")
    @DisplayName("AC-3.2: several spoofed X-User-Id copies in mixed case on a protected request leave exactly the token's sub")
    void spoofedCopiesOnAProtectedRequestAreReplaced() {
        // When
        RawHttp.Response response = RawHttp.send(port, "POST", "/api/orgs",
                "Authorization: " + TestTokens.bearerValid(),
                "X-User-Id: " + TestTokens.FORGED_USER_ID,
                "x-user-id: forged-2",
                "X-USER-ID: forged-3",
                "X-User-Id: " + TestTokens.FORGED_USER_ID + ", forged-4");

        // Then
        assertThat(response.status()).isEqualTo(200);
        RecordedRequest forwarded = AUTH_SERVICE.requests().get(0);
        assertThat(forwarded.headerValues("X-User-Id")).containsExactly(TestTokens.USER_ID);
    }

    @Test
    @Tag("AC-3.3")
    @DisplayName("AC-3.3: spoofed X-User-Id copies on a public request are all removed, even with a valid token")
    void spoofedCopiesOnAPublicRequestAreRemoved() {
        // When
        RawHttp.send(port, "POST", "/api/auth/login",
                "X-User-Id: " + TestTokens.FORGED_USER_ID, "x-user-id: forged-2", "X-USER-ID: forged-3");
        RawHttp.send(port, "POST", "/api/auth/register",
                "Authorization: " + TestTokens.bearerValid(), "X-User-Id: " + TestTokens.FORGED_USER_ID);

        // Then
        assertThat(AUTH_SERVICE.requests()).hasSize(2)
                .allSatisfy(request -> assertThat(request.hasHeader("X-User-Id")).as("X-User-Id in any case").isFalse());
    }

    @Test
    @Tag("AC-3.2")
    @DisplayName("AC-3.2: a spoofed X-User-Id with an invalid token gets 401 and is never forwarded")
    void spoofedHeaderWithAnInvalidTokenIsNotForwarded() {
        // When
        RawHttp.Response response = RawHttp.send(port, "POST", "/api/orgs",
                "Authorization: Bearer " + TestTokens.expired(), "X-User-Id: " + TestTokens.USER_ID);

        // Then
        assertThat(response.status()).isEqualTo(401);
        assertNoStubReceivedARequest();
    }

    static Stream<Arguments> malformedRequestIds() {
        return Stream.of(
                Arguments.of("129 characters", "a".repeat(129)),
                Arguments.of("1000 characters", "b".repeat(1000)),
                Arguments.of("UTF-8 letters", "req-é-ü"),
                Arguments.of("comma list", "a,b"),
                Arguments.of("forged log fields", "x requestId=forged status=200"),
                Arguments.of("slash and colon", "a/b:c"),
                Arguments.of("tab inside", "a\tb"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedRequestIds")
    @Tag("AC-7.3")
    @DisplayName("AC-7.3: a malformed X-Request-Id is replaced by one UUID, the same on the response, downstream and in the log")
    void malformedRequestIdsAreReplaced(String description, String value, CapturedOutput output) {
        // Given the value is sent as UTF-8 bytes over the wire
        byte[] request = ("POST /api/auth/login HTTP/1.1\r\nHost: localhost\r\nX-Request-Id: " + value
                + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8);

        // When
        RawHttp.Response response = RawHttp.sendBytes(port, request);

        // Then
        assertThat(response.headerValues("X-Request-Id")).as("exactly one X-Request-Id on the response").hasSize(1);
        String requestId = response.headerValues("X-Request-Id").get(0);
        assertThat(requestId).matches(QaSupport.UUID_PATTERN);
        assertThat(AUTH_SERVICE.requests()).singleElement()
                .satisfies(forwarded -> assertThat(forwarded.headerValues("X-Request-Id")).containsExactly(requestId));
        assertThat(QaSupport.onlyAccessEntry(output, requestId)).doesNotContain("forged");
        assertThat(output.getAll()).doesNotContain("requestId=forged");
    }

    @Test
    @Tag("AC-7.2")
    @DisplayName("AC-7.2: a 128-character request ID (the maximum) is kept")
    void theLongestValidRequestIdIsKept() {
        // When
        String maximum = "Az09._-".repeat(18) + "AB";
        RawHttp.Response response = RawHttp.send(port, "POST", "/api/auth/login", "X-Request-Id: " + maximum);

        // Then
        assertThat(maximum).hasSize(128);
        assertThat(response.headerValues("X-Request-Id")).containsExactly(maximum);
        assertThat(AUTH_SERVICE.requests().get(0).headerValues("X-Request-Id")).containsExactly(maximum);
    }

    @Test
    @Tag("AC-7.3")
    @DisplayName("AC-7.3: CR/LF in X-Request-Id never becomes a separate header downstream or in the response")
    void crlfInTheRequestIdIsNotInjected() {
        // When: a bare CR inside the value (Tomcat must reject or the gateway must replace it)
        byte[] request = ("POST /api/auth/login HTTP/1.1\r\nHost: localhost\r\nX-Request-Id: abc\rX-Injected: 1"
                + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1);
        RawHttp.Response response = RawHttp.sendBytes(port, request);

        // Then
        assertThat(response.headerValues("X-Injected")).isEmpty();
        assertThat(AUTH_SERVICE.requests()).allSatisfy(forwarded -> assertThat(forwarded.hasHeader("X-Injected")).isFalse());
    }

    @Test
    @Tag("AC-7.1")
    @DisplayName("AC-7.1: every forwarded response carries exactly one X-Request-Id, equal to the one sent downstream")
    void forwardedResponsesCarryExactlyOneRequestId() {
        // When
        RawHttp.Response response = RawHttp.send(port, "POST", "/api/orgs", "Authorization: " + TestTokens.bearerValid());

        // Then
        assertThat(response.headerValues("X-Request-Id")).hasSize(1);
        assertThat(AUTH_SERVICE.requests().get(0).headerValues("X-Request-Id"))
                .containsExactlyElementsOf(response.headerValues("X-Request-Id"));
    }
}
