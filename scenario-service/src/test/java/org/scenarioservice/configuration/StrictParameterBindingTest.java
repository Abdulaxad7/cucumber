package org.scenarioservice.configuration;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.beans.PropertyEditor;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * D16, D19 #6, D20 #4 (unit): the strict, untrimmed editors for path ids, {@code {version}} and {@code archived}.
 * One spelling per value; anything else is rejected, which the web layer turns into 400 MALFORMED_REQUEST.
 */
class StrictParameterBindingTest {

    private static final String ID = "3f9b2d6e-1c4a-4b7e-8f0d-5a2c4e6b8d13";

    @ParameterizedTest(name = "the id \"{0}\" is accepted")
    @ValueSource(strings = {ID, "3F9B2D6E-1C4A-4B7E-8F0D-5A2C4E6B8D13"})
    void canonicalIdsAreAccepted(String text) {
        assertThat(convert(new StrictParameterBinding.CanonicalUuidEditor(), text)).isEqualTo(UUID.fromString(ID));
    }

    @ParameterizedTest(name = "the id \"{0}\" is rejected")
    @ValueSource(strings = {" " + ID, ID + " ", "\t" + ID, "1-1-1-1-1", "not-a-uuid", "",
            "3f9b2d6e-1c4a-4b7e-8f0d-5a2c4e6b8d1", "f9b2d6e-1c4a-4b7e-8f0d-5a2c4e6b8d13",
            "{3f9b2d6e-1c4a-4b7e-8f0d-5a2c4e6b8d13}", "3f9b2d6e1c4a4b7e8f0d5a2c4e6b8d13"})
    void otherIdSpellingsAreRejected(String text) {
        assertRejected(new StrictParameterBinding.CanonicalUuidEditor(), text);
    }

    @ParameterizedTest(name = "the version \"{0}\" is accepted")
    @ValueSource(strings = {"1", "7", "10", "2147483647"})
    void positiveInt32VersionsAreAccepted(String text) {
        assertThat(convert(new StrictParameterBinding.VersionNumberEditor(), text))
                .isEqualTo(new VersionNumber(Integer.parseInt(text)));
    }

    @ParameterizedTest(name = "the version \"{0}\" is rejected")
    @ValueSource(strings = {"0", "01", "+1", "-1", " 1", "1 ", "2147483648", "9999999999", "99999999999", "1.0", "",
            "1e3", "٣"})
    void otherVersionSpellingsAreRejected(String text) {
        assertRejected(new StrictParameterBinding.VersionNumberEditor(), text);
    }

    @ParameterizedTest(name = "archived \"{0}\" is accepted")
    @ValueSource(strings = {"true", "false"})
    void trueAndFalseAreAccepted(String text) {
        assertThat(convert(new StrictParameterBinding.ArchivedFilterEditor(), text))
                .isEqualTo(new ArchivedFilter(Boolean.parseBoolean(text)));
    }

    @ParameterizedTest(name = "archived \"{0}\" is rejected")
    @ValueSource(strings = {"TRUE", "True", "yes", "on", "1", "0", " true", "false ", "", "maybe"})
    void otherArchivedSpellingsAreRejected(String text) {
        assertRejected(new StrictParameterBinding.ArchivedFilterEditor(), text);
    }

    private static Object convert(PropertyEditor editor, String text) {
        editor.setAsText(text);
        return editor.getValue();
    }

    private static void assertRejected(PropertyEditor editor, String text) {
        // A fixed message per editor, so the value is never part of it
        assertThat(catchThrowable(() -> editor.setAsText(text))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageMatching("Not a canonical UUID|Not a version number from 1 to 2147483647|Not true or false");
    }
}
