package org.scenarioservice.configuration;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The shared HS512 secret (D6), set through {@code JWT_SECRET}. Blank stops start-up here; the format rules (no
 * unresolved placeholder, standard Base64, at least 64 bytes) are checked by {@code security/JwtTokenValidator}.
 * {@link #toString()} never shows the secret.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record JwtProperties(
        @NotBlank(message = "app.jwtSecret must be set through the JWT_SECRET environment variable")
        String jwtSecret) {

    @Override
    public String toString() {
        return "JwtProperties[jwtSecret=<redacted>]";
    }
}
