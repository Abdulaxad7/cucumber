package org.scenarioservice.configuration;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Stops start-up when a database setting is missing (D14, E81, E83): a value is rejected when it's null, blank, or
 * exactly one unresolved {@code ${NAME}} (an environment variable that isn't set). A value that merely contains
 * {@code $} or <code>{</code> is a set value. Every rejected setting is named in one failure, by its variable, and
 * no value is ever part of the message. The {@code DataSource} depends on this check
 * ({@link DataSourceDependsOnDatabaseProperties}), so no connection is attempted first.
 */
@Component
public class DatabaseSettingsCheck {

    private static final Pattern UNRESOLVED_PLACEHOLDER = Pattern.compile("\\$\\{[^${}]+}");

    public DatabaseSettingsCheck(DatabaseProperties settings) {
        List<String> missing = new ArrayList<>();
        check(settings.url(), "spring.datasource.url must be set through the SCENARIO_DB_URL environment variable",
                missing);
        check(settings.username(),
                "spring.datasource.username must be set through the SCENARIO_DB_USERNAME environment variable",
                missing);
        check(settings.password(),
                "spring.datasource.password must be set through the SCENARIO_DB_PASSWORD environment variable",
                missing);
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
