package org.apigateway.configuration;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The shared JWT secret (D4, contract C2). The {@code JWT_SECRET} environment variable binds to
 * {@code jwt.secret} directly, so there is no default outside the {@code dev} profile and startup fails
 * when it is missing. Base64 decoding and the 64-byte minimum are checked by
 * {@link org.apigateway.security.JwtSecretDecoder}, which never reports the value.
 */
@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
    @NotBlank(message = "must be set through the JWT_SECRET environment variable")
    String secret
) {

    @Override
    public String toString() {
        return "JwtProperties[secret=<redacted>]";
    }
}
