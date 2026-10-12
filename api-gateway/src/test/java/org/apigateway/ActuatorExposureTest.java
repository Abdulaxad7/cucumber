package org.apigateway;

import org.apigateway.support.GatewayIntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Only the health endpoint is exposed (D7).
 */
class ActuatorExposureTest extends GatewayIntegrationTest {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/actuator/env", "/actuator/beans", "/actuator/configprops", "/actuator/gateway/routes"})
    @Tag("AC-8.5")
    void otherManagementEndpointsAreNotExposed(String path) {
        // When
        HttpResponse<String> response = gateway.sendWithValidToken("GET", path);

        // Then
        assertThat(response.statusCode()).isEqualTo(404);
        assertNoStubReceivedARequest();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/actuator", "/actuator/health/liveness", "/actuator/health/readiness", "/actuator/health/diskSpace"})
    void noOtherHealthOrDiscoveryResourceIsExposed(String path) {
        // When
        HttpResponse<String> response = gateway.sendWithValidToken("GET", path);

        // Then
        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void healthNeedsNoToken() {
        // When
        HttpResponse<String> response = gateway.send("GET", "/actuator/health");

        // Then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"status\":\"UP\"}");
    }
}
