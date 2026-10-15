package com.example.authservice.repository;

import com.example.authservice.support.AuthServiceIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US-4 AC-4.4 (phase 4, T5, E69): every timestamp column of the auth-service database stores its time zone.
 */
@AuthServiceIntegrationTest
class TimestampColumnsTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @Tag("AC-4.4")
    @DisplayName("AC-4.4 Every timestamp column stores its time zone")
    void everyTimestampColumnStoresItsTimeZone() {
        List<String> columns = jdbc.queryForList("""
                SELECT table_name || '.' || column_name || ' ' || data_type FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND column_name IN ('created_at', 'updated_at', 'expires_at', 'archived_at')""", String.class);

        // W4: every timestamp column, pinned. audit_log.created_at went with its table (V7, E74), so there are 9.
        assertThat(columns).containsExactlyInAnyOrder(
                "users.created_at timestamp with time zone", "users.updated_at timestamp with time zone",
                "organizations.created_at timestamp with time zone",
                "org_memberships.created_at timestamp with time zone",
                "projects.created_at timestamp with time zone", "projects.archived_at timestamp with time zone",
                "project_memberships.created_at timestamp with time zone",
                "invitations.expires_at timestamp with time zone", "invitations.created_at timestamp with time zone");
    }
}
