package com.example.authservice.qa;

import com.example.authservice.support.AuthServiceIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * qa ERD check: the schema Hibernate generates (the same DDL as the dev profile, which only changes show-sql and the
 * secret) matches the as-built auth_service group of docs/erd/cucumber.dbml: columns with type, length and
 * nullability, unique constraints and foreign keys. Expected values are transcribed from the ERD file.
 */
@AuthServiceIntegrationTest
class QaSchemaTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("qa ERD: every auth_service column has the documented type, length and nullability")
    void columnsMatchTheErd() {
        Set<String> expected = new TreeSet<>(List.of(
                // users
                "USERS.ID UUID NOT NULL", "USERS.EMAIL CHARACTER VARYING(255) NOT NULL",
                "USERS.PASSWORD_HASH CHARACTER VARYING(255) NULL", "USERS.FULL_NAME CHARACTER VARYING(255) NULL",
                "USERS.STATUS ENUM NULL", "USERS.MFA_ENABLED BOOLEAN NOT NULL", "USERS.CREATED_AT TIMESTAMP(6) NULL",
                "USERS.UPDATED_AT TIMESTAMP(6) NULL",
                // organizations
                "ORGANIZATIONS.ID UUID NOT NULL", "ORGANIZATIONS.NAME CHARACTER VARYING(255) NULL",
                "ORGANIZATIONS.PLAN ENUM NULL", "ORGANIZATIONS.SEAT_LIMIT INTEGER NULL",
                "ORGANIZATIONS.CREATED_AT TIMESTAMP(6) NULL",
                // org_memberships
                "ORG_MEMBERSHIPS.ID UUID NOT NULL", "ORG_MEMBERSHIPS.USER_ID UUID NOT NULL",
                "ORG_MEMBERSHIPS.ORG_ID UUID NOT NULL", "ORG_MEMBERSHIPS.ROLE ENUM NULL",
                "ORG_MEMBERSHIPS.CREATED_AT TIMESTAMP(6) NULL",
                // projects
                "PROJECTS.ID UUID NOT NULL", "PROJECTS.ORG_ID UUID NOT NULL", "PROJECTS.NAME CHARACTER VARYING(255) NULL",
                "PROJECTS.CREATED_AT TIMESTAMP(6) NULL", "PROJECTS.ARCHIVED_AT TIMESTAMP(6) NULL",
                // project_memberships
                "PROJECT_MEMBERSHIPS.ID UUID NOT NULL", "PROJECT_MEMBERSHIPS.USER_ID UUID NOT NULL",
                "PROJECT_MEMBERSHIPS.PROJECT_ID UUID NOT NULL", "PROJECT_MEMBERSHIPS.ROLE ENUM NULL",
                "PROJECT_MEMBERSHIPS.CREATED_AT TIMESTAMP(6) NULL",
                // invitations
                "INVITATIONS.ID UUID NOT NULL", "INVITATIONS.EMAIL CHARACTER VARYING(255) NULL",
                "INVITATIONS.ORG_ID UUID NOT NULL",
                "INVITATIONS.ROLE ENUM NULL", "INVITATIONS.INVITED_BY UUID NOT NULL",
                "INVITATIONS.TOKEN CHARACTER VARYING(255) NULL", "INVITATIONS.STATUS ENUM NULL",
                "INVITATIONS.EXPIRES_AT TIMESTAMP(6) NULL", "INVITATIONS.CREATED_AT TIMESTAMP(6) NULL",
                // audit_log
                "AUDIT_LOG.ID UUID NOT NULL", "AUDIT_LOG.ACTOR_ID UUID NOT NULL",
                "AUDIT_LOG.ACTION CHARACTER VARYING(255) NULL", "AUDIT_LOG.ORG_ID UUID NULL",
                "AUDIT_LOG.PROJECT_ID UUID NULL", "AUDIT_LOG.TARGET_USER_ID UUID NULL", "AUDIT_LOG.METADATA JSON NULL",
                "AUDIT_LOG.CREATED_AT TIMESTAMP(6) NULL"));

        Set<String> actual = new TreeSet<>(jdbc.query("""
                SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, IS_NULLABLE, CHARACTER_MAXIMUM_LENGTH, DATETIME_PRECISION
                FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = 'PUBLIC'""", (rs, n) -> {
            String type = rs.getString("DATA_TYPE");
            if (type.equals("CHARACTER VARYING")) {
                type += "(" + rs.getLong("CHARACTER_MAXIMUM_LENGTH") + ")";
            } else if (type.equals("TIMESTAMP")) {
                type += "(" + rs.getInt("DATETIME_PRECISION") + ")";
            }
            return rs.getString("TABLE_NAME") + "." + rs.getString("COLUMN_NAME") + " " + type + " "
                    + (rs.getString("IS_NULLABLE").equals("YES") ? "NULL" : "NOT NULL");
        }));

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    @DisplayName("qa ERD: the documented enums have exactly the documented values")
    void enumValuesMatchTheErd() {
        Set<String> actual = new TreeSet<>(jdbc.query("""
                SELECT e.OBJECT_NAME, c.COLUMN_NAME, e.VALUE_NAME FROM INFORMATION_SCHEMA.ENUM_VALUES e
                JOIN INFORMATION_SCHEMA.COLUMNS c ON c.TABLE_NAME = e.OBJECT_NAME AND c.DTD_IDENTIFIER = e.ENUM_IDENTIFIER
                WHERE e.OBJECT_SCHEMA = 'PUBLIC'""",
                (rs, n) -> rs.getString(1) + "." + rs.getString(2) + "=" + rs.getString(3)));

        assertThat(actual).containsExactlyInAnyOrder(
                "USERS.STATUS=ACTIVE", "USERS.STATUS=SUSPENDED", "USERS.STATUS=PENDING_VERIFICATION",
                "ORGANIZATIONS.PLAN=FREE", "ORGANIZATIONS.PLAN=TEAM", "ORGANIZATIONS.PLAN=ENTERPRISE",
                "ORG_MEMBERSHIPS.ROLE=OWNER", "ORG_MEMBERSHIPS.ROLE=ADMIN", "ORG_MEMBERSHIPS.ROLE=MEMBER",
                "ORG_MEMBERSHIPS.ROLE=READER",
                "PROJECT_MEMBERSHIPS.ROLE=ADMIN", "PROJECT_MEMBERSHIPS.ROLE=MEMBER", "PROJECT_MEMBERSHIPS.ROLE=TESTER",
                "PROJECT_MEMBERSHIPS.ROLE=READER",
                "INVITATIONS.STATUS=PENDING", "INVITATIONS.STATUS=ACCEPTED", "INVITATIONS.STATUS=REVOKED", "INVITATIONS.STATUS=EXPIRED",
                "INVITATIONS.ROLE=OWNER", "INVITATIONS.ROLE=ADMIN", "INVITATIONS.ROLE=MEMBER", "INVITATIONS.ROLE=READER");
    }

    @Test
    @DisplayName("qa ERD: unique constraints and foreign keys are exactly the documented ones")
    void constraintsMatchTheErd() {
        Set<String> uniques = new TreeSet<>(jdbc.query("""
                SELECT tc.TABLE_NAME, LISTAGG(k.COLUMN_NAME, ',') WITHIN GROUP (ORDER BY k.COLUMN_NAME)
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
                JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE k
                  ON k.CONSTRAINT_NAME = tc.CONSTRAINT_NAME AND k.TABLE_SCHEMA = tc.TABLE_SCHEMA
                WHERE tc.TABLE_SCHEMA = 'PUBLIC' AND tc.CONSTRAINT_TYPE = 'UNIQUE'
                GROUP BY tc.TABLE_NAME, tc.CONSTRAINT_NAME""", (rs, n) -> rs.getString(1) + "(" + rs.getString(2) + ")"));
        Set<String> foreignKeys = new TreeSet<>(jdbc.query("""
                SELECT k1.TABLE_NAME, k1.COLUMN_NAME, k2.TABLE_NAME, k2.COLUMN_NAME
                FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS rc
                JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE k1 ON k1.CONSTRAINT_NAME = rc.CONSTRAINT_NAME
                JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE k2 ON k2.CONSTRAINT_NAME = rc.UNIQUE_CONSTRAINT_NAME
                WHERE rc.CONSTRAINT_SCHEMA = 'PUBLIC'""",
                (rs, n) -> rs.getString(1) + "." + rs.getString(2) + ">" + rs.getString(3) + "." + rs.getString(4)));

        assertThat(uniques).containsExactlyInAnyOrder("USERS(EMAIL)", "INVITATIONS(TOKEN)",
                "ORG_MEMBERSHIPS(ORG_ID,USER_ID)", "PROJECT_MEMBERSHIPS(PROJECT_ID,USER_ID)");
        assertThat(foreignKeys).containsExactlyInAnyOrder(
                "ORG_MEMBERSHIPS.USER_ID>USERS.ID", "ORG_MEMBERSHIPS.ORG_ID>ORGANIZATIONS.ID",
                "PROJECTS.ORG_ID>ORGANIZATIONS.ID", "PROJECT_MEMBERSHIPS.USER_ID>USERS.ID",
                "PROJECT_MEMBERSHIPS.PROJECT_ID>PROJECTS.ID", "INVITATIONS.ORG_ID>ORGANIZATIONS.ID",
                "INVITATIONS.INVITED_BY>USERS.ID",
                "AUDIT_LOG.ACTOR_ID>USERS.ID", "AUDIT_LOG.ORG_ID>ORGANIZATIONS.ID",
                "AUDIT_LOG.PROJECT_ID>PROJECTS.ID", "AUDIT_LOG.TARGET_USER_ID>USERS.ID");
    }
}
