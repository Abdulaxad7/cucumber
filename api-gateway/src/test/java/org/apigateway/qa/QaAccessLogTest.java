package org.apigateway.qa;

import org.apigateway.support.GatewayIntegrationTest;
import org.apigateway.support.StubResponse;
import org.apigateway.support.TestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.apigateway.qa.QaSupport.onlyAccessEntry;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * QA checks for the access log (D7, AC-7.4) on requests the backend-swe tests do not cover: refused CORS requests,
 * 404 with a valid token, firewall rejections and downstream failures. Each gets exactly one entry.
 */
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("QA: one access entry for every rejected or failed request")
class QaAccessLogTest extends GatewayIntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    @Tag("AC-7.4")
    @DisplayName("AC-7.4: a refused CORS request gets one entry with status 403 and neither service nor user")
    void refusedCorsRequest(CapturedOutput output) {
        // When (no origin is allowed without the dev profile)
        RawHttp.send(port, "POST", "/api/orgs", "Origin: https://evil.example",
                "Authorization: " + TestTokens.bearerValid(), "X-Request-Id: qa-log-403");

        // Then
        String entry = onlyAccessEntry(output, "qa-log-403");
        assertThat(entry).contains(" method=POST ", " path=/api/orgs ", " status=403 ").doesNotContain(" service=", " userId=");
    }

    @Test
    @Tag("AC-7.4")
    @DisplayName("AC-7.4: a 404 with a valid token gets one entry with the user but no service")
    void notFoundWithAValidToken(CapturedOutput output) {
        // When
        RawHttp.send(port, "GET", "/api/projects/1", "Authorization: " + TestTokens.bearerValid(), "X-Request-Id: qa-log-404");

        // Then
        String entry = onlyAccessEntry(output, "qa-log-404");
        assertThat(entry).contains(" status=404 ", " userId=" + TestTokens.USER_ID + " ").doesNotContain(" service=");
    }

    @Test
    @Tag("AC-7.4")
    @DisplayName("AC-7.4: a request rejected by the HTTP firewall still gets exactly one entry")
    void firewallRejection(CapturedOutput output) {
        // When
        RawHttp.Response response = RawHttp.send(port, "POST", "/api/auth/./login", "X-Request-Id: qa-log-400");

        // Then
        assertThat(response.status()).isEqualTo(400);
        assertThat(onlyAccessEntry(output, "qa-log-400")).contains(" status=400 ");
    }

    @Test
    @Tag("AC-7.4")
    @DisplayName("AC-7.4: a dropped downstream connection (502) gets exactly one entry with method, path and status")
    void downstreamFailure(CapturedOutput output) {
        // Given
        SCENARIO_SERVICE.respondWith(StubResponse.dropConnection());

        // When
        RawHttp.send(port, "GET", "/api/scenarios/42?secret=qa-log-query",
                "Authorization: " + TestTokens.bearerValid(), "X-Request-Id: qa-log-502");

        // Then
        String entry = onlyAccessEntry(output, "qa-log-502");
        assertThat(entry).contains(" method=GET ", " path=/api/scenarios/42 ", " status=502 ").containsPattern(" durationMs=\\d+ ");
        assertThat(output.getAll()).doesNotContain("qa-log-query");
    }
}
