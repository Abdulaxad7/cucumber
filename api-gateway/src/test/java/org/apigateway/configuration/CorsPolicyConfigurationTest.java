package org.apigateway.configuration;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.apigateway.support.GatewayIntegrationTest;
import org.apigateway.support.StubResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-origin access for a browser frontend (D5, US-6) with the allowed origins "http://localhost:5173".
 */
@TestPropertySource(properties = "CORS_ALLOWED_ORIGINS=" + CorsPolicyConfigurationTest.ALLOWED_ORIGIN)
class CorsPolicyConfigurationTest extends GatewayIntegrationTest {

    static final String ALLOWED_ORIGIN = "http://localhost:5173";
    private static final String UNKNOWN_ORIGIN = "https://evil.example";

    @Test
    @Tag("AC-6.1")
    void theGatewayAnswersAPreflightRequestFromAnAllowedOrigin() {
        // When
        HttpResponse<String> response = gateway.send(gateway.request("/api/orgs")
                .header("Origin", ALLOWED_ORIGIN)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization, content-type")
                .method("OPTIONS", BodyPublishers.noBody()));

        // Then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().allValues("Access-Control-Allow-Origin")).containsExactly(ALLOWED_ORIGIN);
        assertThat(response.headers().firstValue("Access-Control-Allow-Methods")).hasValueSatisfying(
                methods -> assertThat(methods).contains("POST"));
        assertThat(response.headers().firstValue("Access-Control-Allow-Headers")).hasValueSatisfying(
                headers -> assertThat(headers.toLowerCase()).contains("authorization", "content-type"));
        assertThat(response.headers().firstValue("Access-Control-Max-Age")).hasValue("1800");
        assertThat(response.headers().firstValue("Access-Control-Allow-Credentials")).isEmpty();
        assertNoStubReceivedARequest();
    }

    @Test
    @Tag("AC-6.2")
    void aPreflightRequestFromAnUnknownOriginIsRefused() {
        // When
        HttpResponse<String> response = gateway.send(gateway.request("/api/orgs")
                .header("Origin", UNKNOWN_ORIGIN)
                .header("Access-Control-Request-Method", "POST")
                .method("OPTIONS", BodyPublishers.noBody()));

        // Then
        assertRefusedCorsRequest(response, "/api/orgs");
        assertNoStubReceivedARequest();
    }

    @Test
    @Tag("AC-6.2")
    void anActualRequestFromAnUnknownOriginIsRefusedTheSameWay() {
        // When
        HttpResponse<String> response = gateway.send(gateway.request("/api/auth/login")
                .header("Origin", UNKNOWN_ORIGIN)
                .POST(BodyPublishers.noBody()));

        // Then
        assertRefusedCorsRequest(response, "/api/auth/login");
        assertNoStubReceivedARequest();
    }

    @Test
    @Tag("AC-6.3")
    void anAllowedOriginCanReadTheResponseAndItsRequestId() {
        // When
        HttpResponse<String> response = gateway.send(gateway.request("/api/auth/login")
                .header("Origin", ALLOWED_ORIGIN)
                .POST(BodyPublishers.noBody()));

        // Then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().allValues("Access-Control-Allow-Origin")).containsExactly(ALLOWED_ORIGIN);
        assertThat(response.headers().firstValue("Access-Control-Expose-Headers")).hasValueSatisfying(
                exposed -> assertThat(exposed).contains("X-Request-Id"));
    }

    @Test
    @Tag("AC-6.4")
    void anAllowedOriginCanReadA401FromTheGateway() {
        // When
        HttpResponse<String> response = gateway.send(gateway.request("/api/orgs")
                .header("Origin", ALLOWED_ORIGIN)
                .POST(BodyPublishers.noBody()));

        // Then
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().allValues("Access-Control-Allow-Origin")).containsExactly(ALLOWED_ORIGIN);
    }

    @Test
    @Tag("AC-6.6")
    void corsHeadersSetByAServiceAreNotDuplicated() {
        // Given
        AUTH_SERVICE.respondWith(StubResponse.status(200).withHeader("Access-Control-Allow-Origin", "*"));

        // When
        HttpResponse<String> response = gateway.send(gateway.request("/api/auth/login")
                .header("Origin", ALLOWED_ORIGIN)
                .POST(BodyPublishers.noBody()));

        // Then
        assertThat(response.headers().allValues("Access-Control-Allow-Origin")).containsExactly(ALLOWED_ORIGIN);
    }

    private static void assertRefusedCorsRequest(HttpResponse<String> response, String path) {
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json");
        DocumentContext body = JsonPath.parse(response.body());
        assertThat(body.read("$.status", Integer.class)).isEqualTo(403);
        assertThat(body.read("$.title", String.class)).isEqualTo("Forbidden");
        assertThat(body.read("$.instance", String.class)).isEqualTo(path);
        assertThat(body.read("$.detail", String.class)).isNotBlank();
        assertThat(body.read("$.requestId", String.class))
                .isEqualTo(response.headers().firstValue("X-Request-Id").orElseThrow());
        assertThat(response.headers().map().keySet())
                .noneSatisfy(name -> assertThat(name.toLowerCase()).startsWith("access-control-allow-"));
    }
}
