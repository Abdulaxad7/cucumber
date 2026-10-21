package com.example.authservice.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase-4 D10 and E82: production and dev wait at most 5 s for a lock. Tests run with 1 s
 * ({@code support/TestDatabase}), so this test reads the configured value from the files themselves.
 */
class LockTimeoutSettingTest {

    private static final String OPTIONS = "spring.datasource.hikari.data-source-properties.options";

    @Test
    @DisplayName("D10/E82: application.yaml sets lock_timeout 5 s, and the dev profile doesn't change it")
    void applicationYamlSetsAFiveSecondLockTimeout() throws Exception {
        assertThat(load("application.yaml")).extracting(source -> source.getProperty(OPTIONS))
                .containsExactly("-c lock_timeout=5s");
        assertThat(load("application-dev.yaml")).allSatisfy(source -> assertThat(source.getProperty(OPTIONS)).isNull());
    }

    private static List<PropertySource<?>> load(String file) throws Exception {
        return new YamlPropertySourceLoader().load(file, new ClassPathResource(file));
    }
}
