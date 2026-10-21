package org.scenarioservice.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SE6a, D20 #7, G8 (read as a resource; the dev profile is never activated, D15): {@code application-dev.yaml} holds
 * exactly the {@code .env} import, the dev URL and username, and the dev SQL level; no password, no JWT secret, and
 * never {@code show-sql}.
 */
class DevProfileFileTest {

    @Test
    @DisplayName("SE6a: the dev file holds exactly the .env import, the URL, the username and org.hibernate.SQL DEBUG")
    void theDevFileHoldsExactlyTheApprovedSettings() throws Exception {
        assertThat(devSettings()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "spring.config.import", "optional:file:.env[.properties],optional:file:../.env[.properties]",
                "spring.datasource.url", "jdbc:postgresql://localhost:5432/scenario_service",
                "spring.datasource.username", "scenario_service",
                "logging.level.org.hibernate.SQL", "DEBUG"));
    }

    @Test
    @DisplayName("G8, G12: the dev file has no password or jwtSecret key and doesn't turn show-sql on")
    void theDevFileHoldsNoSecret() throws Exception {
        assertThat(devSettings().keySet()).noneMatch(key -> key.toLowerCase(Locale.ROOT).contains("password"))
                .noneMatch(key -> key.toLowerCase(Locale.ROOT).contains("jwtsecret"))
                .noneMatch(key -> key.toLowerCase(Locale.ROOT).contains("secret"));
        assertThat(String.valueOf(devSettings().get("spring.jpa.show-sql"))).isNotEqualToIgnoringCase("true");
    }

    private static Map<String, String> devSettings() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application-dev.yaml", new ClassPathResource("application-dev.yaml"));
        Map<String, String> settings = new LinkedHashMap<>();
        for (PropertySource<?> source : sources) {
            EnumerablePropertySource<?> enumerable = (EnumerablePropertySource<?>) source;
            Arrays.stream(enumerable.getPropertyNames())
                    .forEach(name -> settings.put(name, String.valueOf(enumerable.getProperty(name))));
        }
        return settings;
    }
}
