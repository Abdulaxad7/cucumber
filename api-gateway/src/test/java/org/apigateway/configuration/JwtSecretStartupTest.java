package org.apigateway.configuration;

import org.apigateway.support.GatewayLauncher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Fail-fast secret loading (D4, AC-8.3): without the dev profile, the gateway needs a usable JWT_SECRET.
 */
@ExtendWith(OutputCaptureExtension.class)
class JwtSecretStartupTest {

    static Stream<Arguments> unusableSecrets() {
        byte[] tooShort = new byte[32];
        new SecureRandom().nextBytes(tooShort);
        return Stream.of(
                Arguments.of("not set", Optional.empty()),
                Arguments.of("not standard Base64", Optional.of("some-secret-key-that-is-long-enough")),
                Arguments.of("Base64 of 32 random bytes", Optional.of(Base64.getEncoder().encodeToString(tooShort))));
    }

    @ParameterizedTest(name = "JWT_SECRET {0}")
    @MethodSource("unusableSecrets")
    @Tag("AC-8.3")
    void theGatewayRefusesToStartWithoutAUsableJwtSecret(String description, Optional<String> secret,
                                                         CapturedOutput output) {
        // Given the dev profile is not active and JWT_SECRET is unusable
        Map<String, Object> environment = GatewayLauncher.testEnvironment();
        environment.remove("JWT_SECRET");
        secret.ifPresent(value -> environment.put("JWT_SECRET", value));

        // When
        Throwable failure = catchThrowable(() -> GatewayLauncher.run(environment).close());

        // Then
        assertThat(failure).as("startup failure").isNotNull();
        assertThat(messagesOf(failure)).anySatisfy(message -> assertThat(message).contains("JWT_SECRET"));
        assertThat(output.getAll()).contains("JWT_SECRET");
        secret.ifPresent(value -> assertThat(output.getAll()).doesNotContain(value));
    }

    private static List<String> messagesOf(Throwable failure) {
        List<String> messages = new ArrayList<>();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            messages.add(String.valueOf(current.getMessage()));
        }
        return messages;
    }
}
