package org.apigateway.exception;

import org.apigateway.support.GatewayIntegrationTest;
import org.apigateway.support.StubResponse;
import org.apigateway.support.StubServer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.http.HttpResponse;
import java.time.Duration;

import static org.apigateway.exception.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Gateway-generated errors with every downstream stub running (D6, US-5).
 */
class GatewayErrorResponseTest extends GatewayIntegrationTest {

    @Test
    @Tag("AC-5.1")
    void a401UsesTheProblemDetailsFormat() {
        // Given no token is sent
        // When
        HttpResponse<String> response = gateway.send("POST", "/api/orgs");

        // Then
        assertProblem(response, 401, "Unauthorized", "/api/orgs");
    }

    @Test
    @Tag("AC-5.1")
    void a404UsesTheProblemDetailsFormat() {
        // Given a valid token is sent
        // When
        HttpResponse<String> response = gateway.sendWithValidToken("GET", "/api/projects/1");

        // Then
        assertProblem(response, 404, "Not Found", "/api/projects/1");
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "GET,  /api/projects/1",
            "POST, /api/authx/login",
            "GET,  /api",
            "GET,  /h2-console",
            "GET,  /swagger-ui/index.html",
            "GET,  /v3/api-docs"
    })
    @Tag("AC-1.5")
    void aPathThatMatchesNoRouteGetsA404ProblemAndReachesNoService(String method, String path) {
        // When
        HttpResponse<String> response = gateway.sendWithValidToken(method, path);

        // Then
        assertProblem(response, 404, "Not Found", path);
        assertNoStubReceivedARequest();
    }

    @Test
    @Tag("AC-4.5")
    void aServiceThatDropsTheConnectionProduces502() {
        // Given
        SCENARIO_SERVICE.respondWith(StubResponse.dropConnection());

        // When
        HttpResponse<String> response = gateway.sendWithValidToken("GET", "/api/scenarios/42");

        // Then
        assertProblem(response, 502, "Bad Gateway", "/api/scenarios/42");
    }

    @ParameterizedTest(name = "{0} {1} -> 502, at most {3} request(s) to {2}")
    @CsvSource({
            "POST,   /api/orgs,                                      auth-service,   1",
            "PUT,    /api/orgs/3b9d5c1e-0000-4000-8000-0000000000aa, auth-service,   1",
            "PATCH,  /api/orgs/3b9d5c1e-0000-4000-8000-0000000000aa, auth-service,   1",
            "DELETE, /api/orgs/3b9d5c1e-0000-4000-8000-0000000000aa, auth-service,   1",
            "GET,    /api/reports/42,                                report-service, 2"
    })
    @Tag("AC-4.8")
    void onlyASafeReadIsEverSentAgainAfterTheServiceDropsTheConnection(String method, String path, String service,
                                                                       int maxRequests) {
        // Given
        StubServer owner = stub(service);
        owner.respondWith(StubResponse.dropConnection());

        // When
        HttpResponse<String> response = gateway.sendWithValidToken(method, path);

        // Then
        assertThat(response.statusCode()).isEqualTo(502);
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(4)).untilAsserted(() ->
                assertThat(owner.requests())
                        .as("requests %s %s received by %s", method, path, service)
                        .filteredOn(request -> request.method().equals(method) && request.path().equals(path))
                        .hasSizeBetween(1, maxRequests));
    }

    @Test
    void theInstanceExcludesTheQueryString() {
        // When
        HttpResponse<String> response = gateway.sendWithValidToken("GET", "/api/projects/1?page=2");

        // Then
        assertProblem(response, 404, "Not Found", "/api/projects/1");
        assertThat(response.body()).doesNotContain("page=2");
    }
}
