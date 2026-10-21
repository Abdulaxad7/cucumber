package org.scenarioservice.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The shared full-context test configuration (D15, P18): the whole application with MockMvc, the shared PostgreSQL
 * test database ({@link TestDatabase}, through {@code @ServiceConnection}), the TEST-ONLY JWT secret and the access
 * check stub's address ({@link TestSettings}). No profile is active, so no {@code .env} is ever loaded (G10). Tests
 * reuse this one configuration unless they need different properties, so the number of cached contexts stays small.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@ImportTestcontainers({TestDatabase.class, TestSettings.class})
public @interface ScenarioServiceIntegrationTest {
}
