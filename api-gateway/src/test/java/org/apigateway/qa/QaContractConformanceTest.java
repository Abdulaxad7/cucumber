package org.apigateway.qa;

import org.apigateway.support.GatewayIntegrationTest;
import org.apigateway.support.GatewayLauncher;
import org.apigateway.support.StubResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.net.http.HttpResponse;
import java.time.Duration;

import static org.apigateway.qa.QaSupport.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * QA spot checks of real responses against docs/api/api-gateway.openapi.yaml: the Problem schema (exact required
 * field set, no extras) for 503 and 504, the X-Request-Id header pattern, and the 401 challenge.
 */
@TestPropertySource(properties = "GATEWAY_RESPONSE_TIMEOUT=2s")
@DisplayName("QA: responses conform to the edge contract")
class QaContractConformanceTest extends GatewayIntegrationTest {

    private static final String NOTHING_LISTENING = "http://localhost:" + GatewayLauncher.freePort();

    @DynamicPropertySource
    static void scenarioServiceIsDown(DynamicPropertyRegistry registry) {
        registry.add("gateway.downstream.scenario-service-url", () -> NOTHING_LISTENING);
    }

    @Test
    @Tag("AC-5.1")
    @DisplayName("Contract ServiceUnavailable: 503 has exactly the Problem fields and X-Request-Id")
    void serviceUnavailableMatchesTheContract() {
        // When
        HttpResponse<String> response = gateway.sendWithValidToken("GET", "/api/scenarios/42?x=1");

        // Then
        assertContractProblem(response, 503, "Service Unavailable", "/api/scenarios/42");
    }

    @Test
    @Tag("AC-5.1")
    @DisplayName("Contract GatewayTimeout: 504 has exactly the Problem fields and X-Request-Id")
    void gatewayTimeoutMatchesTheContract() {
        // Given
        REPORT_SERVICE.respondWith(StubResponse.ok().after(Duration.ofSeconds(4)));

        // When
        HttpResponse<String> response = gateway.sendWithValidToken("GET", "/api/reports/42");

        // Then
        assertContractProblem(response, 504, "Gateway Timeout", "/api/reports/42");
    }

    @Test
    @Tag("AC-2.7")
    @DisplayName("Contract Unauthorized: 401 has WWW-Authenticate starting with Bearer and exactly the Problem fields")
    void unauthorizedMatchesTheContract() {
        // When
        HttpResponse<String> response = gateway.send("POST", "/api/orgs");

        // Then
        assertContractProblem(response, 401, "Unauthorized", "/api/orgs");
        assertThat(response.headers().allValues("WWW-Authenticate")).singleElement()
                .satisfies(challenge -> assertThat(challenge).matches("^Bearer.*"));
    }

    @Test
    @Tag("AC-7.1")
    @DisplayName("Contract XRequestId: a passed-through 2xx carries exactly one X-Request-Id matching the pattern")
    void passedThroughResponsesCarryTheRequestId() {
        // When
        HttpResponse<String> response = gateway.send("POST", "/api/auth/login");

        // Then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().allValues("X-Request-Id")).singleElement()
                .satisfies(id -> assertThat(id).matches(QaSupport.REQUEST_ID_PATTERN));
    }

    private static void assertContractProblem(HttpResponse<String> response, int status, String title, String instance) {
        assertProblem(response.statusCode(), response.headers().firstValue("Content-Type").orElse(null),
                response.headers().firstValue("X-Request-Id").orElse(null), response.body(), status, title, instance);
        assertThat(response.headers().allValues("X-Request-Id")).hasSize(1);
    }
}
