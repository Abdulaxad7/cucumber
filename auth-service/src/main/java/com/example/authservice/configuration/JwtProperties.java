package com.example.authservice.configuration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

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
