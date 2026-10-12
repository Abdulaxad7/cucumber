package org.apigateway.exception;

import org.apigateway.support.GatewayIntegrationTest;
import org.apigateway.support.StubResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.net.http.HttpResponse;
import java.time.Duration;

import static org.apigateway.exception.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * A service slower than the response timeout (D6, US-4, US-5).
 */
@TestPropertySource(properties = "GATEWAY_RESPONSE_TIMEOUT=2s")
class DownstreamTimeoutTest extends GatewayIntegrationTest {

    private static final Duration STUB_DELAY = Duration.ofSeconds(5);

    @Test
    @Tag("AC-4.2")
    void aServiceSlowerThanTheResponseTimeoutProduces504() {
        // Given
        SCENARIO_SERVICE.respondWith(StubResponse.ok().after(STUB_DELAY));

        // When
        long start = System.nanoTime();
        HttpResponse<String> response = gateway.sendWithValidToken("GET", "/api/scenarios/42");
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        // Then
        assertThat(response.statusCode()).isEqualTo(504);
        assertThat(elapsed).isLessThan(Duration.ofSeconds(4));
    }

    @Test
    @Tag("AC-4.3")
    void theGatewayNeverRetriesAFailedRequest() {
        // Given
        AUTH_SERVICE.respondWith(StubResponse.status(201).after(STUB_DELAY));

        // When
        HttpResponse<String> response = gateway.sendWithValidToken("POST", "/api/orgs");

        // Then
        assertThat(response.statusCode()).isEqualTo(504);
        await().during(Duration.ofSeconds(6))
                .atMost(Duration.ofSeconds(7))
                .until(() -> AUTH_SERVICE.requestCount() == 1);
    }

    @Test
    @Tag("AC-5.1")
    void a504UsesTheProblemDetailsFormat() {
        // Given
        SCENARIO_SERVICE.respondWith(StubResponse.ok().after(STUB_DELAY));

        // When
        HttpResponse<String> response = gateway.sendWithValidToken("GET", "/api/scenarios/42");

        // Then
        assertProblem(response, 504, "Gateway Timeout", "/api/scenarios/42");
    }
}
