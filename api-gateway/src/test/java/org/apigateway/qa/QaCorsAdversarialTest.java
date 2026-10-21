package org.apigateway.qa;

import org.apigateway.support.GatewayIntegrationTest;
import org.apigateway.support.StubResponse;
import org.apigateway.support.TestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.TestPropertySource;

import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.util.Locale;

import static org.apigateway.qa.QaSupport.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * QA adversarial CORS checks (D5) with the allowed origins "http://localhost:5173": origin look-alikes, the
 * {@code null} origin, credentials, preflight forwarding and CORS headers on gateway-generated errors.
 */
@TestPropertySource(properties = "CORS_ALLOWED_ORIGINS=" + QaCorsAdversarialTest.ALLOWED_ORIGIN)
@DisplayName("QA: CORS origin tricks, credentials and error responses")
class QaCorsAdversarialTest extends GatewayIntegrationTest {

    static final String ALLOWED_ORIGIN = "http://localhost:5173";

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"null", "http://localhost:5173.evil.com", "http://evil.localhost:5173", "http://localhost:51730",
            "https://localhost:5173", "http://localhost", "http://localhost:3000", "*", "http://127.0.0.1:5173",
            "http://localhost:5173@evil.com"})
    @Tag("AC-6.2")
    @DisplayName("AC-6.2: a preflight from an origin look-alike is refused with the 403 Problem and no CORS grant")
    void lookAlikeOriginsAreRefused(String origin) {
        // When
        HttpResponse<String> response = gateway.send(gateway.request("/api/orgs")
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .method("OPTIONS", BodyPublishers.noBody()));

        // Then
        assertRefused(response, "/api/orgs");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/api/orgs", "/api/auth/login", "/api/scenarios/42"})
    @Tag("AC-6.2")
    @DisplayName("AC-6.2: an actual request with a valid token but a disallowed Origin is refused and not forwarded")
    void actualRequestsFromDisallowedOriginsAreRefusedEvenWithAToken(String path) {
        // When
        HttpResponse<String> response = gateway.send(gateway.request(path)
                .header("Origin", "http://localhost:5173.evil.com")
                .header("Authorization", TestTokens.bearerValid())
                .POST(BodyPublishers.noBody()));

        // Then
        assertRefused(response, path);
    }

    @Test
    @Tag("AC-6.1")
    @DisplayName("AC-6.1: an allowed preflight carries the D5 policy, never credentials, and is not forwarded even with a token")
    void allowedPreflightMatchesThePolicy() {
        // When
        HttpResponse<String> response = gateway.send(gateway.request("/api/orgs")
                .header("Origin", ALLOWED_ORIGIN)
                .header("Authorization", TestTokens.bearerValid())
                .header("Access-Control-Request-Method", "DELETE")
                .header("Access-Control-Request-Headers", "authorization, content-type, accept, x-request-id")
                .method("OPTIONS", BodyPublishers.noBody()));

        // Then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().allValues("Access-Control-Allow-Origin")).containsExactly(ALLOWED_ORIGIN);
        assertThat(response.headers().firstValue("Access-Control-Allow-Methods").orElseThrow().replace(" ", ""))
                .isEqualTo("GET,POST,PUT,PATCH,DELETE,OPTIONS");
        assertThat(response.headers().firstValue("Access-Control-Allow-Headers").orElseThrow().toLowerCase(Locale.ROOT))
                .contains("authorization", "content-type", "accept", "x-request-id");
        assertThat(response.headers().firstValue("Access-Control-Max-Age")).hasValue("1800");
        assertThat(response.headers().firstValue("Access-Control-Allow-Credentials")).isEmpty();
        assertThat(response.headers().firstValue("X-Request-Id")).isPresent();
        assertNoStubReceivedARequest();
    }

    @Test
    @Tag("AC-6.1")
    @DisplayName("AC-6.1: a preflight from an allowed origin does not grant X-User-Id or Cookie as request headers")
    void identityAndCookieHeadersAreNotGranted() {
        // When
        HttpResponse<String> userId = preflightRequestingHeader("x-user-id");
        HttpResponse<String> cookie = preflightRequestingHeader("cookie");

        // Then
        for (HttpResponse<String> response : java.util.List.of(userId, cookie)) {
            assertThat(response.statusCode()).isNotEqualTo(200);
            assertThat(response.headers().firstValue("Access-Control-Allow-Headers")).isEmpty();
        }
        assertNoStubReceivedARequest();
    }

    @Test
    @Tag("AC-6.6")
    @DisplayName("AC-6.6: downstream CORS headers (wildcard origin, credentials) never reach the client")
    void downstreamCorsHeadersAreDropped() {
        // Given
        AUTH_SERVICE.respondWith(StubResponse.status(200)
                .withHeader("Access-Control-Allow-Origin", "*")
                .withHeader("Access-Control-Allow-Credentials", "true")
                .withHeader("Access-Control-Expose-Headers", "Set-Cookie"));

        // When
        HttpResponse<String> response = gateway.send(gateway.request("/api/auth/login")
                .header("Origin", ALLOWED_ORIGIN).POST(BodyPublishers.noBody()));

        // Then
        assertThat(response.headers().allValues("Access-Control-Allow-Origin")).containsExactly(ALLOWED_ORIGIN);
        assertThat(response.headers().firstValue("Access-Control-Allow-Credentials")).isEmpty();
        assertThat(response.headers().allValues("Access-Control-Expose-Headers")).hasSize(1)
                .first().asString().doesNotContain("Set-Cookie").contains("X-Request-Id");
    }

    @Test
    @Tag("AC-6.4")
    @DisplayName("D5 / AC-6.4: gateway-generated 404 and 502 errors carry the CORS grant for an allowed origin")
    void gatewayErrorsCarryCorsHeadersForAllowedOrigins() {
        // Given
        SCENARIO_SERVICE.respondWith(StubResponse.dropConnection());

        // When
        HttpResponse<String> notFound = gateway.send(gateway.request("/api/projects/1")
                .header("Origin", ALLOWED_ORIGIN).header("Authorization", TestTokens.bearerValid()).GET());
        HttpResponse<String> badGateway = gateway.send(gateway.request("/api/scenarios/42")
                .header("Origin", ALLOWED_ORIGIN).header("Authorization", TestTokens.bearerValid()).GET());

        // Then
        assertThat(notFound.statusCode()).isEqualTo(404);
        assertThat(notFound.headers().allValues("Access-Control-Allow-Origin")).containsExactly(ALLOWED_ORIGIN);
        assertThat(badGateway.statusCode()).isEqualTo(502);
        assertThat(badGateway.headers().allValues("Access-Control-Allow-Origin")).containsExactly(ALLOWED_ORIGIN);
    }

    private HttpResponse<String> preflightRequestingHeader(String header) {
        return gateway.send(gateway.request("/api/orgs")
                .header("Origin", ALLOWED_ORIGIN)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", header)
                .method("OPTIONS", BodyPublishers.noBody()));
    }

    private static void assertRefused(HttpResponse<String> response, String path) {
        assertProblem(response.statusCode(), response.headers().firstValue("Content-Type").orElse(null),
                response.headers().firstValue("X-Request-Id").orElse(null), response.body(), 403, "Forbidden", path);
        assertThat(response.headers().map().keySet())
                .noneSatisfy(name -> assertThat(name.toLowerCase(Locale.ROOT)).startsWith("access-control-allow-"));
        assertNoStubReceivedARequest();
    }
}
