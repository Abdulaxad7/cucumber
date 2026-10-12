package org.apigateway;

import com.jayway.jsonpath.JsonPath;
import org.apigateway.support.GatewayClient;
import org.apigateway.support.GatewayLauncher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The gateway's health endpoint (D7, AC-8.4).
 */
class HealthEndpointTest {

    @Test
    @Tag("AC-8.4")
    void healthIsPublicAndDoesNotDependOnDownstreamServices() {
        // Given nothing is listening at the auth-service, scenario-service or report-service URLs
        Map<String, Object> environment = GatewayLauncher.testEnvironment();
        environment.put("AUTH_SERVICE_URL", "http://localhost:" + GatewayLauncher.freePort());
        environment.put("SCENARIO_SERVICE_URL", "http://localhost:" + GatewayLauncher.freePort());
        environment.put("REPORT_SERVICE_URL", "http://localhost:" + GatewayLauncher.freePort());

        try (ConfigurableApplicationContext context = GatewayLauncher.run(environment)) {
            GatewayClient gateway = new GatewayClient(((WebServerApplicationContext) context).getWebServer().getPort());

            // When
            HttpResponse<String> response = gateway.send("GET", "/actuator/health");

            // Then
            assertThat(response.statusCode()).isEqualTo(200);
            Map<String, Object> body = JsonPath.parse(response.body()).read("$");
            assertThat(body).containsExactly(Map.entry("status", "UP"));
        }
    }
}
