package org.apigateway.exception;

import org.apigateway.support.GatewayIntegrationTest;
import org.apigateway.support.GatewayLauncher;
import org.apigateway.support.StubResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.http.HttpResponse;
import java.time.Duration;

import static org.apigateway.exception.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Nothing is listening at the configured scenario-service URL (D6, US-4, US-5).
 */
class DownstreamUnavailableTest extends GatewayIntegrationTest {

    private static final int NOTHING_LISTENING_PORT = GatewayLauncher.freePort();
    private static final String SCENARIO_SERVICE_URL = "http://localhost:" + NOTHING_LISTENING_PORT;

    @DynamicPropertySource
    static void scenarioServiceIsDown(DynamicPropertyRegistry registry) {
        registry.add("gateway.downstream.scenario-service-url", () -> SCENARIO_SERVICE_URL);
    }

    @Test
    @Tag("AC-4.1")
    void aServiceThatIsNotRunningProduces503Quickly() {
        // When
        long start = System.nanoTime();
        HttpResponse<String> response = gateway.sendWithValidToken("GET", "/api/scenarios/42");
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        // Then
        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(elapsed).isLessThan(Duration.ofSeconds(3));
    }

    @Test
    @Tag("AC-4.4")
    void otherRoutesKeepWorkingWhileOneServiceIsDown() {
        // Given
        AUTH_SERVICE.respondWith(StubResponse.status(200));

        // When
        HttpResponse<String> response = gateway.send("POST", "/api/auth/login");

        // Then
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    @Tag("AC-5.1")
    void a503UsesTheProblemDetailsFormat() {
        // When
        HttpResponse<String> response = gateway.sendWithValidToken("GET", "/api/scenarios/42");

        // Then
        assertProblem(response, 503, "Service Unavailable", "/api/scenarios/42");
    }

    @Test
    @Tag("AC-5.2")
    void gatewayErrorsDoNotRevealInternalDetails() {
        // Given the scenario-service URL is http://localhost:<port> and nothing is listening there
        // When
        HttpResponse<String> response = gateway.sendWithValidToken("GET", "/api/scenarios/42");

        // Then
        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).doesNotContain(
                "localhost", String.valueOf(NOTHING_LISTENING_PORT), "Exception", "at org.", "at java.");
    }
}
