package org.scenarioservice.support;

import org.springframework.boot.testcontainers.context.ImportTestcontainers;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Connects a Spring test context that isn't a {@link ScenarioServiceIntegrationTest} (for example a
 * {@code @DataJpaTest}) to the shared PostgreSQL test database: it imports {@link TestDatabase}, whose container
 * Boot's {@code @ServiceConnection} turns into the context's connection details.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ImportTestcontainers(TestDatabase.class)
public @interface UsesTestDatabase {
}
