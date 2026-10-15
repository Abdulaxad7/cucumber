package com.example.authservice.configuration;

import com.example.authservice.AuthServiceApplication;
import com.example.authservice.support.TestJwt;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.util.ArrayList;
import java.util.List;

import static com.example.authservice.support.TestDatabase.POSTGRES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * P7: the service refuses to start when {@code app.orgs.free-seat-limit} isn't a positive number of seats.
 */
class OrganizationPropertiesStartupTest {

    @Test
    void theServiceRefusesToStartWithANonPositiveFreeSeatLimit() {
        // When
        Throwable failure = catchThrowable(() -> new SpringApplicationBuilder(AuthServiceApplication.class)
                // Port 0 keeps 8081 free; the context gets the test database's connection settings.
                .run("--server.port=0", "--spring.profiles.active=", "--app.jwtSecret=" + TestJwt.SECRET,
                        "--app.orgs.free-seat-limit=0",
                        "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "--spring.datasource.username=" + POSTGRES.getUsername(),
                        "--spring.datasource.password=" + POSTGRES.getPassword())
                .close());

        // Then
        assertThat(failure).as("startup failure").isNotNull();
        assertThat(messagesOf(failure)).anySatisfy(message -> assertThat(message).contains("app.orgs"));
    }

    private static List<String> messagesOf(Throwable failure) {
        List<String> messages = new ArrayList<>();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            messages.add(String.valueOf(current.getMessage()));
        }
        return messages;
    }
}
