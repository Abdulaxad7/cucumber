package com.example.authservice.configuration;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.orgs")
public record OrganizationProperties(
        @Positive(message = "app.orgs.free-seat-limit must be a positive number of seats")
        @DefaultValue("5")
        int freeSeatLimit
) {
}
