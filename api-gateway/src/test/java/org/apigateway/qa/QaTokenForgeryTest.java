package org.apigateway.qa;

import org.apigateway.support.GatewayIntegrationTest;
import org.apigateway.support.TestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.stream.Stream;

import static org.apigateway.qa.QaSupport.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * QA adversarial checks for edge authentication (D4, US-2): forged, tampered and malformed bearer credentials
 * on a protected route never reach a service and always get the 401 Problem with a Bearer challenge.
 */
@DisplayName("QA: forged and malformed bearer credentials are rejected at the edge")
class QaTokenForgeryTest extends GatewayIntegrationTest {

    static Stream<Arguments> forgedTokens() {
        String header512 = "{\"alg\":\"HS512\"}";
        String[] valid = TestTokens.valid().split("\\.");
        return Stream.of(
                Arguments.of("alg none, no signature", TestTokens.unsigned()),
                Arguments.of("alg NONE (upper case), no signature",
                        QaSupport.handMade("{\"alg\":\"NONE\"}", QaSupport.validClaimsJson(), "")),
                Arguments.of("alg none with the valid token's signature kept",
                        QaSupport.handMade("{\"alg\":\"none\"}", QaSupport.validClaimsJson(), valid[2])),
                Arguments.of("HS256 signed with the shared secret", TestTokens.signedWithHs256()),
                Arguments.of("HS384 signed with the shared secret", QaSupport.hs384WithSharedSecret()),
                Arguments.of("HS512 signed with another secret", TestTokens.signedWithAnotherSecret()),
                Arguments.of("payload sub altered, signature kept", TestTokens.withAlteredSubject(TestTokens.FORGED_USER_ID)),
                Arguments.of("expired token with exp edited into the future, signature kept", QaSupport.expiredWithExtendedExp()),
                Arguments.of("signature stripped", QaSupport.signatureStripped()),
                Arguments.of("signature spliced from another valid token", QaSupport.splicedSignature()),
                Arguments.of("expired 1 hour ago", TestTokens.expired()),
                Arguments.of("expired 2 seconds ago (no clock skew, A3)", QaSupport.expiredAgo(Duration.ofSeconds(2))),
                Arguments.of("no exp claim", TestTokens.withoutExpiration()),
                Arguments.of("JWE-style header (alg dir, enc A256GCM)",
                        QaSupport.handMade("{\"alg\":\"dir\",\"enc\":\"A256GCM\"}", QaSupport.validClaimsJson(), valid[2])),
                Arguments.of("HS512 header, garbage signature", QaSupport.handMade(header512, QaSupport.validClaimsJson(), "AAAA")),
                Arguments.of("only two segments", valid[0] + "." + valid[1]),
                Arguments.of("four segments", TestTokens.valid() + ".AAAA"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("forgedTokens")
    @Tag("AC-2.3")
    @DisplayName("AC-2.3: a forged or tampered token on POST /api/orgs gets the 401 Problem and reaches no service")
    void forgedTokensAreRejected(String description, String token) {
        // Given a protected route and a token that must not authenticate
        HttpRequest.Builder request = gateway.request("/api/orgs")
                .header("Authorization", "Bearer " + token)
                .POST(BodyPublishers.ofString("{\"name\":\"Acme QA\"}"));

        // When
        HttpResponse<String> response = gateway.send(request);

        // Then
        assertRejected(response);
    }

    static Stream<Arguments> subjectVariants() {
        return Stream.of(
                Arguments.of("no sub claim", QaSupport.hs512WithSubject(null)),
                Arguments.of("empty sub", QaSupport.hs512WithSubject("")),
                Arguments.of("blank sub", QaSupport.hs512WithSubject("   ")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("subjectVariants")
    @Tag("AC-2.4")
    @DisplayName("AC-2.4: a correctly signed, unexpired token without a non-empty sub is rejected")
    void tokensWithoutANonEmptySubjectAreRejected(String description, String token) {
        // When
        HttpResponse<String> response = gateway.send(gateway.request("/api/orgs")
                .header("Authorization", "Bearer " + token)
                .POST(BodyPublishers.noBody()));

        // Then
        assertRejected(response);
    }

    static Stream<Arguments> malformedAuthorizationHeaders() {
        String valid = TestTokens.valid();
        return Stream.of(
                Arguments.of("two spaces after Bearer", "Bearer  " + valid),
                Arguments.of("tab instead of a space", "Bearer\t" + valid),
                Arguments.of("two tokens separated by a space", "Bearer " + valid + " " + valid),
                Arguments.of("two credentials separated by a comma", "Bearer " + valid + ", Bearer " + valid),
                Arguments.of("scheme only", "Bearer"),
                Arguments.of("another scheme with a valid JWT", "Token " + valid),
                Arguments.of("a valid JWT without a scheme", valid),
                Arguments.of("Basic credentials", "Basic YW5hOlNlY3JldDEyMyE="));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedAuthorizationHeaders")
    @Tag("AC-2.3")
    @DisplayName("AC-2.3: a malformed Authorization header is rejected even when it contains a valid JWT")
    void malformedAuthorizationHeadersAreRejected(String description, String authorization) {
        // When
        HttpResponse<String> response = gateway.send(gateway.request("/api/orgs")
                .header("Authorization", authorization)
                .POST(BodyPublishers.noBody()));

        // Then
        assertRejected(response);
    }

    @Test
    @Tag("AC-2.3")
    @DisplayName("AC-2.3 / A2: a valid token sent in the query string or a cookie does not authenticate")
    void tokensOutsideTheAuthorizationHeaderAreIgnored() {
        // When
        HttpResponse<String> inQuery = gateway.send(gateway.request("/api/orgs?access_token=" + TestTokens.valid())
                .POST(BodyPublishers.noBody()));
        HttpResponse<String> inCookie = gateway.send(gateway.request("/api/orgs")
                .header("Cookie", "access_token=" + TestTokens.valid())
                .POST(BodyPublishers.noBody()));

        // Then
        assertThat(inQuery.statusCode()).isEqualTo(401);
        assertThat(inCookie.statusCode()).isEqualTo(401);
        assertNoStubReceivedARequest();
    }

    @Test
    @Tag("AC-2.2")
    @DisplayName("Control: the same request with the valid token is forwarded, so the rejections above are not blanket")
    void theValidTokenIsAccepted() {
        // When
        HttpResponse<String> response = gateway.sendWithValidToken("POST", "/api/orgs");

        // Then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(AUTH_SERVICE.requests()).hasSize(1);
    }

    @Test
    @Tag("AC-3.1")
    @DisplayName("AC-3.1: X-User-Id carries the sub of the presented token, not a fixed value")
    void theIdentityHeaderFollowsTheTokenSubject() {
        // Given a valid token for another user
        String token = QaSupport.hs512WithSubject("qa-user-42");

        // When
        gateway.send(gateway.request("/api/orgs").header("Authorization", "Bearer " + token).POST(BodyPublishers.noBody()));

        // Then
        assertThat(AUTH_SERVICE.requests()).singleElement()
                .satisfies(request -> assertThat(request.headerValues("X-User-Id")).containsExactly("qa-user-42"));
    }

    private static void assertRejected(HttpResponse<String> response) {
        assertProblem(response.statusCode(), response.headers().firstValue("Content-Type").orElse(null),
                response.headers().firstValue("X-Request-Id").orElse(null), response.body(), 401, "Unauthorized", "/api/orgs");
        assertThat(response.headers().firstValue("WWW-Authenticate")).as("AC-2.7 challenge")
                .hasValueSatisfying(challenge -> assertThat(challenge).startsWith("Bearer"));
        assertNoStubReceivedARequest();
    }
}
