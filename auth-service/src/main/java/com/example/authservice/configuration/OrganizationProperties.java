package com.example.authservice.configuration;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Organization settings (D11), bound from {@code app.orgs.*}. {@code app.orgs.free-seat-limit} is the seat limit of
 * every organization created in phase 1 (plan {@code FREE}); the service refuses to start if it isn't positive.
 */
@Validated
@ConfigurationProperties(prefix = "app.orgs")
public record OrganizationProperties(
        @Positive(message = "app.orgs.free-seat-limit must be a positive number of seats")
        @DefaultValue("5")
        int freeSeatLimit
) {
}
