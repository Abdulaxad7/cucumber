package org.apigateway;

import org.apigateway.support.GatewayClient;
import org.apigateway.support.GatewayLauncher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The gateway port (D3, AC-8.2). Binding port 8080 itself is checked manually (AC-8.6), because an
 * automated test must not depend on a fixed port being free.
 */
class ServerPortTest {

    @Test
    @Tag("AC-8.2")
    void theGatewayListensOn8080WithNoPortSetting() {
        // Given / When
        ConfigurableEnvironment configuration = GatewayLauncher.resolveConfiguration(Map.of());

        // Then
        assertThat(configuration.getProperty("server.port", Integer.class)).isEqualTo(8080);
    }

    @Test
    @Tag("AC-8.2")
    void theServerPortEnvironmentVariableOverridesTheDefault() {
        // Given / When
        ConfigurableEnvironment configuration = GatewayLauncher.resolveConfiguration(Map.of("SERVER_PORT", "9080"));

        // Then
        assertThat(configuration.getProperty("server.port", Integer.class)).isEqualTo(9080);
    }

    @Test
    @Tag("AC-8.2")
    void healthAnswersOnThePortGivenBySERVER_PORT() {
        // Given
        int port = GatewayLauncher.freePort();
        Map<String, Object> environment = GatewayLauncher.testEnvironment();
        environment.put("SERVER_PORT", String.valueOf(port));

        try (ConfigurableApplicationContext ignored = GatewayLauncher.run(environment)) {
            // When
            HttpResponse<String> response = new GatewayClient(port).send("GET", "/actuator/health");

            // Then
            assertThat(response.statusCode()).isEqualTo(200);
        }
    }
}
