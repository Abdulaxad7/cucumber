package org.scenarioservice.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * D14 settings check (E81, E83 as auth-service proved them): a database setting is rejected only when it's missing,
 * blank, or exactly one unresolved {@code ${NAME}}; every rejected setting is named in one failure, and no value is
 * ever part of the message.
 */
class DatabaseSettingsCheckTest {

    private static final String URL = "jdbc:postgresql://db.example:5432/scenario_service";

    @Test
    @Tag("AC-8.12")
    @DisplayName("AC-8.12: all three settings missing are named in one failure")
    void allThreeMissingSettingsAreNamedInOneFailure() {
        Throwable failure = catchThrowable(() -> new DatabaseSettingsCheck(new DatabaseProperties(null, "", " \t")));

        assertThat(failure).isInstanceOf(IllegalStateException.class).hasMessage(
                "spring.datasource.url must be set through the SCENARIO_DB_URL environment variable; "
                        + "spring.datasource.username must be set through the SCENARIO_DB_USERNAME "
                        + "environment variable; spring.datasource.password must be set through the "
                        + "SCENARIO_DB_PASSWORD environment variable");
    }

    @ParameterizedTest(name = "AC-8.12: the password \"{0}\" is rejected by name, without its value")
    @ValueSource(strings = {"", "   ", "\n", "${SCENARIO_DB_PASSWORD}", "${QA_UNSET}", "${a.b-c_d}"})
    @Tag("AC-8.12")
    void aBlankOrUnresolvedPasswordIsRejectedByName(String password) {
        Throwable failure = catchThrowable(() -> new DatabaseSettingsCheck(
                new DatabaseProperties(URL, "scenario_service", password)));

        assertThat(failure).isInstanceOf(IllegalStateException.class).hasMessage(
                "spring.datasource.password must be set through the SCENARIO_DB_PASSWORD environment variable");
    }

    @ParameterizedTest(name = "AC-8.13: the set value \"{0}\" passes the check")
    @ValueSource(strings = {"Qa$Pw-61", "Pw$QA{61}", "Pw{QA}61$", "${", "}${", "$${A}", "${A}${B}", "x${A}"})
    @Tag("AC-8.13")
    void aValueThatOnlyContainsDollarOrBracesIsSet(String password) {
        assertThatCode(() -> new DatabaseSettingsCheck(new DatabaseProperties(URL, "scenario_service", password)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("G12: the properties record never shows a value")
    void thePropertiesRecordIsRedacted() {
        assertThat(new DatabaseProperties(URL, "scenario_service", "Pw-Marker-QA").toString())
                .doesNotContain("db.example", "scenario_service", "Pw-Marker-QA");
    }
}
