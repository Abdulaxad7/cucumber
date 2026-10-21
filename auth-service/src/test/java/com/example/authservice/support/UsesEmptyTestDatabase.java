package com.example.authservice.support;

import org.springframework.test.context.jdbc.Sql;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * For {@code @DataJpaTest} classes (phase-4 D2): connects to the shared PostgreSQL test database
 * ({@link UsesTestDatabase}) and, before each test method, deletes every row, children first, inside the test's own
 * transaction. Each test so starts from an empty database, as it did on its own embedded database, and the test's
 * rollback restores the rows that other tests left. No schema object is created or dropped. The never-written
 * {@code audit_log} table isn't listed.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@UsesTestDatabase
@Sql(statements = {
        "DELETE FROM invitations",
        "DELETE FROM project_memberships",
        "DELETE FROM projects",
        "DELETE FROM org_memberships",
        "DELETE FROM organizations",
        "DELETE FROM users"})
public @interface UsesEmptyTestDatabase {
}
