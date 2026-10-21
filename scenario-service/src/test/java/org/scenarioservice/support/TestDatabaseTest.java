package org.scenarioservice.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.scenarioservice.support.TestDatabase.POSTGRES;

/**
 * T2 smoke test (D14 items 1 and 3, P19): the shared test database is PostgreSQL 17 with UTF-8 and the builtin
 * {@code C.UTF-8} locale, and its ICU root collation lower-cases as the path rule needs (D10, AC-1.17).
 */
class TestDatabaseTest {

    @Test
    @DisplayName("D14: the test database is PostgreSQL 17, UTF-8, locale provider builtin with C.UTF-8")
    void theTestDatabaseIsPostgresSeventeenWithTheBuiltinLocale() throws Exception {
        assertThat(Integer.parseInt(query("SHOW server_version_num")) / 10000).isEqualTo(17);
        assertThat(query("SELECT datlocprovider::text || ' ' || datlocale || ' ' || pg_encoding_to_char(encoding) "
                + "FROM pg_database WHERE datname = current_database()")).isEqualTo("b C.UTF-8 UTF8");
    }

    @Test
    @DisplayName("P19: und-x-icu exists, its ICU knows Unicode 14 or later, and it lower-cases İ like Java")
    void theIcuRootCollationLowerCasesLikeJava() throws Exception {
        assertThat(query("SELECT collprovider FROM pg_collation WHERE collname = 'und-x-icu'")).isEqualTo("i");
        String unicode = query("SELECT icu_unicode_version()");
        assertThat(Integer.parseInt(unicode.split("\\.")[0])).as("ICU Unicode version " + unicode)
                .isGreaterThanOrEqualTo(14);
        assertThat(query("SELECT lower('İzmir' COLLATE \"und-x-icu\")")).isEqualTo("i\u0307zmir");
        // U+2C2F was added in Unicode 14, so Java 17 doesn't lower-case it; ICU does (AC-1.17 row 3)
        assertThat(query("SELECT lower(U&'\\2C2F' COLLATE \"und-x-icu\")")).isEqualTo("\u2C5F");
        assertThat("\u2C2F".toLowerCase(Locale.ROOT)).isEqualTo("\u2C2F");
    }

    private static String query(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getString(1);
        }
    }
}
