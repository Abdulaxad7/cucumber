package com.example.authservice.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Full-context acceptance test (D10): the real security chain, exception handling and the shared PostgreSQL test
 * database ({@link TestDatabase}), driven through MockMvc, with the TEST-ONLY JWT secret. Tests using it are not
 * {@code @Transactional}; they reset data in {@code @BeforeEach}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = "app.jwtSecret=" + TestJwt.SECRET)
@AutoConfigureMockMvc
@UsesTestDatabase
@Import(AcmeFixture.class)
public @interface AuthServiceIntegrationTest {
}
