package com.example.authservice.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.datasource")
public record DatabaseProperties(String url, String username, String password) {

    @Override
    public String toString() {
        return "DatabaseProperties[url=<redacted>, username=<redacted>, password=<redacted>]";
    }
}
