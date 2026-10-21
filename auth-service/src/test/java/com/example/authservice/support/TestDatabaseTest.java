package com.example.authservice.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static com.example.authservice.support.TestDatabase.POSTGRES;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase-4 T2 smoke test: the shared test database is PostgreSQL 17 with UTF-8 and the builtin {@code C.UTF-8} locale
 * (D3).
 */
class TestDatabaseTest {

    @Test
    @DisplayName("D3: the test database is PostgreSQL 17, UTF-8, locale provider builtin with C.UTF-8")
    void theTestDatabaseIsPostgresSeventeenWithTheBuiltinLocale() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            try (ResultSet version = statement.executeQuery("SHOW server_version_num")) {
                version.next();
                assertThat(Integer.parseInt(version.getString(1)) / 10000).isEqualTo(17);
            }
            try (ResultSet database = statement.executeQuery("SELECT datlocprovider, datlocale, "
                    + "pg_encoding_to_char(encoding) FROM pg_database WHERE datname = current_database()")) {
                database.next();
                assertThat(database.getString(1)).isEqualTo("b");
                assertThat(database.getString(2)).isEqualTo("C.UTF-8");
                assertThat(database.getString(3)).isEqualTo("UTF8");
            }
        }
    }
}
