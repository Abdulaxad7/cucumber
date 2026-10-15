package com.example.authservice.configuration;

import com.example.authservice.AuthServiceApplication;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;

import static com.example.authservice.support.TestDatabase.POSTGRES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * AC-3.9: without the dev profile, auth-service refuses to start unless app.jwtSecret is standard Base64 of at
 * least 64 bytes, and the failure names the setting but never shows the value.
 */
@ExtendWith(OutputCaptureExtension.class)
class JwtSecretStartupTest {

    /**
     * Stands in for an unset JWT_SECRET whatever the test environment contains: application.yaml binds
     * app.jwtSecret to ${JWT_SECRET}, and this placeholder is just as unresolvable.
     */
    private static final String UNSET = "${AUTH_SERVICE_TEST_VARIABLE_THAT_IS_NEVER_SET}";

    static Stream<Arguments> unusableSecrets() {
        byte[] tooShort = new byte[32];
        new SecureRandom().nextBytes(tooShort);
        return Stream.of(
                Arguments.of("not set", UNSET),
                Arguments.of("\"not base64 !!\"", "not base64 !!"),
                Arguments.of("the Base64 encoding of 32 random bytes", Base64.getEncoder().encodeToString(tooShort)));
    }

    @ParameterizedTest(name = "AC-3.9 app.jwtSecret {0}")
    @MethodSource("unusableSecrets")
    @Tag("AC-3.9")
    void theServiceRefusesToStartWithAnUnusableSecret(String row, String secret, CapturedOutput output) {
        // When auth-service starts without the dev profile
        Throwable failure = catchThrowable(() -> start(secret).close());

        // Then
        assertThat(failure).as("startup failure").isNotNull();
        assertThat(messagesOf(failure)).anySatisfy(message -> assertThat(message).contains("app.jwtSecret"));
        if (!secret.equals(UNSET)) {
            assertThat(messagesOf(failure)).noneSatisfy(message -> assertThat(message).contains(secret));
            assertThat(output.getAll()).doesNotContain(secret);
        }
    }

    private static ConfigurableApplicationContext start(String secret) {
        // Command-line arguments take precedence over the environment; port 0 keeps 8081 free. The context gets the
        // test database's connection settings.
        return new SpringApplicationBuilder(AuthServiceApplication.class)
                .run("--server.port=0", "--spring.profiles.active=", "--app.jwtSecret=" + secret,
                        "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "--spring.datasource.username=" + POSTGRES.getUsername(),
                        "--spring.datasource.password=" + POSTGRES.getPassword());
    }

    private static List<String> messagesOf(Throwable failure) {
        List<String> messages = new ArrayList<>();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            messages.add(String.valueOf(current.getMessage()));
        }
        return messages;
    }
}
