package com.example.authservice.support;

import org.springframework.boot.testcontainers.context.ImportTestcontainers;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Connects a Spring test context to the shared PostgreSQL test database (phase-4 D2): it imports
 * {@link TestDatabase}, whose container Boot's {@code @ServiceConnection} turns into the context's connection
 * details, and whose {@code @DynamicPropertySource} sets the test connection pool. {@link AuthServiceIntegrationTest}
 * carries it; {@link UsesEmptyTestDatabase} carries it for the {@code @DataJpaTest} classes; other Spring tests add
 * it themselves.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ImportTestcontainers(TestDatabase.class)
public @interface UsesTestDatabase {
}
