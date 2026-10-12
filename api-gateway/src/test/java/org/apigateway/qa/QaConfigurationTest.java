package org.apigateway.qa;

import org.apigateway.support.GatewayClient;
import org.apigateway.support.GatewayLauncher;
import org.apigateway.support.StubServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * QA checks for operator settings (D3, D4, D5, P1): JWT_SECRET boundaries, service URLs from real environment
 * variables, and fail-fast validation of other settings. Each case starts its own gateway with a controlled environment.
 */
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("QA: operator settings and fail-fast startup")
class QaConfigurationTest {

    static Stream<Arguments> unusableSecrets() {
        byte[] sixtyFour = filled(64, (byte) 0xFB);
        return Stream.of(
                Arguments.of("63 bytes (one below the HS512 minimum)", Base64.getEncoder().encodeToString(filled(63, (byte) 0x5A))),
                Arguments.of("64 bytes in the URL-safe Base64 alphabet", Base64.getUrlEncoder().encodeToString(sixtyFour)),
                Arguments.of("64 bytes with a trailing newline", Base64.getEncoder().encodeToString(sixtyFour) + "\n"),
                Arguments.of("64 bytes in MIME line-wrapped form", Base64.getMimeEncoder().encodeToString(sixtyFour)),
                Arguments.of("blank", "   "));
    }

    @ParameterizedTest(name = "JWT_SECRET {0}")
    @MethodSource("unusableSecrets")
    @Tag("AC-8.3")
    @DisplayName("AC-8.3: an unusable JWT_SECRET stops startup, names JWT_SECRET and is never printed")
    void unusableSecretsStopStartup(String description, String secret, CapturedOutput output) {
        // Given the dev profile is not active
        Map<String, Object> environment = GatewayLauncher.testEnvironment();
        environment.put("JWT_SECRET", secret);

        // When
        Throwable failure = catchThrowable(() -> GatewayLauncher.run(environment).close());

        // Then
        assertThat(failure).as("startup failure").isNotNull();
        assertThat(messagesOf(failure)).anySatisfy(message -> assertThat(message).contains("JWT_SECRET"));
        if (!secret.isBlank()) {
            assertThat(output.getAll()).doesNotContain(secret.strip());
        }
    }

    /**
     * BUG-api-gateway-1. The DEV ONLY secret and the dev CORS origins must apply only when an operator activates the
     * {@code dev} profile. If the base {@code application.yaml} activates it, every environment silently runs on the
     * committed secret and AC-8.3 can no longer fail fast.
     */
    @Test
    @Tag("BUG-api-gateway-1")
    @DisplayName("BUG-api-gateway-1 (D3, D4, AC-8.3 precondition): the base configuration activates no profile")
    void theBaseConfigurationActivatesNoProfile() {
        // When no profile is requested and no environment variable is set
        String[] activeProfiles = GatewayLauncher.resolveConfiguration(Map.of()).getActiveProfiles();

        // Then
        assertThat(activeProfiles).as("profiles activated by application.yaml itself").isEmpty();
    }

    @Test
    @Tag("AC-8.3")
    @DisplayName("AC-8.3 boundary: exactly 64 bytes of standard Base64 is accepted")
    void exactly64BytesIsAccepted() {
        // Given
        Map<String, Object> environment = GatewayLauncher.testEnvironment();
        environment.put("JWT_SECRET", Base64.getEncoder().encodeToString(filled(64, (byte) 0xFB)));

        // When
        try (ConfigurableApplicationContext context = GatewayLauncher.run(environment)) {
            HttpResponse<String> health = clientFor(context).send("GET", "/actuator/health");

            // Then
            assertThat(health.statusCode()).isEqualTo(200);
        }
    }

    @Test
    @Tag("AC-8.1")
    @DisplayName("AC-8.1: the AUTH_SERVICE_URL environment variable (not a Spring property) chooses the auth-service")
    void theAuthServiceUrlEnvironmentVariableIsUsed() {
        // Given
        try (StubServer authService = StubServer.start("qa-auth-service")) {
            Map<String, Object> environment = GatewayLauncher.testEnvironment();
            environment.put("AUTH_SERVICE_URL", authService.baseUrl());

            try (ConfigurableApplicationContext context = GatewayLauncher.run(environment)) {
                // When
                HttpResponse<String> response = clientFor(context).send("POST", "/api/auth/login");

                // Then
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(authService.requests()).singleElement()
                        .satisfies(request -> assertThat(request.path()).isEqualTo("/api/auth/login"));
            }
        }
    }

    static Stream<Arguments> invalidSettings() {
        return Stream.of(
                Arguments.of("CORS_ALLOWED_ORIGINS", "*"),
                Arguments.of("CORS_ALLOWED_ORIGINS", "https://*.example.com"),
                Arguments.of("GATEWAY_CONNECT_TIMEOUT", "0s"),
                Arguments.of("GATEWAY_RESPONSE_TIMEOUT", "-1s"));
    }

    @ParameterizedTest(name = "{0}={1}")
    @MethodSource("invalidSettings")
    @Tag("AC-6.5")
    @DisplayName("D5 / P1: wildcard origins and non-positive timeouts stop startup with a message naming the setting")
    void invalidSettingsStopStartup(String setting, String value) {
        // Given
        Map<String, Object> environment = GatewayLauncher.testEnvironment();
        environment.put(setting, value);

        // When
        Throwable failure = catchThrowable(() -> GatewayLauncher.run(environment).close());

        // Then
        assertThat(failure).as("startup failure").isNotNull();
        assertThat(messagesOf(failure)).anySatisfy(message -> assertThat(message).contains(setting));
    }

    private static GatewayClient clientFor(ConfigurableApplicationContext context) {
        return new GatewayClient(((WebServerApplicationContext) context).getWebServer().getPort());
    }

    private static byte[] filled(int length, byte value) {
        byte[] bytes = new byte[length];
        Arrays.fill(bytes, value);
        return bytes;
    }

    private static List<String> messagesOf(Throwable failure) {
        List<String> messages = new ArrayList<>();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            messages.add(String.valueOf(current.getMessage()));
        }
        return messages;
    }
}
