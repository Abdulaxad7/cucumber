package com.example.authservice.configuration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Token settings (D2 of the gateway spec, contract C2), bound from the existing keys {@code app.jwtSecret}
 * (environment variable {@code JWT_SECRET}, no default outside the dev profile) and {@code app.jwtExpirationInMs}.
 * The secret's Base64 form and its 64-byte minimum are checked when the token provider starts, with messages that
 * never contain the value.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record JwtProperties(
        @NotBlank(message = "app.jwtSecret must be set through the JWT_SECRET environment variable")
        String jwtSecret,

        @Positive(message = "app.jwtExpirationInMs must be a positive number of milliseconds")
        long jwtExpirationInMs
) {

    @Override
    public String toString() {
        return "JwtProperties[jwtSecret=<redacted>, jwtExpirationInMs=" + jwtExpirationInMs + "]";
    }
}
