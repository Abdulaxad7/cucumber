package org.scenarioservice.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.scenarioservice.support.ScenarioServiceIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D14 item 12 (refined, D20 #3): production and dev wait at most 5 s for a lock, read from the files themselves; the
 * test support uses 1 s, which a test connection reports.
 */
@ScenarioServiceIntegrationTest
class LockTimeoutSettingTest {

    private static final String OPTIONS = "spring.datasource.hikari.data-source-properties.options";

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("D14: application.yaml sets lock_timeout 5 s and logServerErrorDetail false; dev changes neither")
    void applicationYamlSetsAFiveSecondLockTimeout() throws Exception {
        List<PropertySource<?>> application = load("application.yaml");
        assertThat(application).extracting(source -> source.getProperty(OPTIONS)).containsExactly("-c lock_timeout=5s");
        assertThat(application).extracting(source -> String.valueOf(source.getProperty(
                "spring.datasource.hikari.data-source-properties.logServerErrorDetail"))).containsExactly("false");
        assertThat(load("application-dev.yaml")).allSatisfy(source -> {
            assertThat(source.getProperty(OPTIONS)).isNull();
            assertThat(source.getProperty("spring.datasource.hikari.data-source-properties.logServerErrorDetail"))
                    .isNull();
        });
    }

    @Test
    @DisplayName("D20 #3: a test connection waits at most 1 s for a lock")
    void aTestConnectionHasAOneSecondLockTimeout() {
        assertThat(jdbc.queryForObject("SHOW lock_timeout", String.class)).isEqualTo("1s");
    }

    private static List<PropertySource<?>> load(String file) throws Exception {
        return new YamlPropertySourceLoader().load(file, new ClassPathResource(file));
    }
}
