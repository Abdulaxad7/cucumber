package com.example.authservice.configuration;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class DatabaseSettingsCheck {

    private static final Pattern UNRESOLVED_PLACEHOLDER = Pattern.compile("\\$\\{[^${}]+}");

    public DatabaseSettingsCheck(DatabaseProperties settings) {
        List<String> missing = new ArrayList<>();
        check(settings.url(), "spring.datasource.url must be set through the AUTH_DB_URL environment variable",
                missing);
        check(settings.username(),
                "spring.datasource.username must be set through the AUTH_DB_USERNAME environment variable", missing);
        check(settings.password(),
                "spring.datasource.password must be set through the AUTH_DB_PASSWORD environment variable", missing);
        if (!missing.isEmpty()) {
            throw new IllegalStateException(String.join("; ", missing));
        }
    }

    private static void check(String value, String message, List<String> missing) {
        if (value == null || value.isBlank() || UNRESOLVED_PLACEHOLDER.matcher(value).matches()) {
            missing.add(message);
        }
    }
}
