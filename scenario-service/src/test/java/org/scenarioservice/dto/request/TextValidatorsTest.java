package org.scenarioservice.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D8, D10, D19 #9, D20 #4 (unit): the text validators, each rule in one place. Lengths count code points; U+0000 and
 * lone surrogates are never storable; control characters are rule T1's set (C0, DEL, C1); Unicode-blank is
 * persistence rule T2's set. No message contains the value.
 */
class TextValidatorsTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    record Limited(@MaxCodePoints(value = 3, message = "too long") String value) {
    }

    record Storable(@StorableText(message = "not storable") String value) {
    }

    record NoControls(@NoControlCharacters(message = "control character") String value) {
    }

    record TrimmedNoControls(@NoControlCharacters(trimmed = true, message = "control character") String value) {
    }

    record Path(@VisiblePathParts(message = "invisible part") String value) {
    }

    @AfterAll
    static void close() {
        FACTORY.close();
    }

    @Test
    @DisplayName("D8: lengths count code points, so a four-byte character counts once")
    void lengthsCountCodePoints() {
        assertThat(valid(new Limited("😀😀😀"))).isTrue();
        assertThat(valid(new Limited("abcd"))).isFalse();
        assertThat(valid(new Limited(null))).isTrue();
    }

    @ParameterizedTest(name = "D8: \"{0}\" (escaped) is not storable")
    @ValueSource(strings = {"a\u0000b", "a\uD800b", "a\uDC00b", "a\uD800", "\uDC00"})
    void nulAndLoneSurrogatesAreNotStorable(String value) {
        assertThat(messages(new Storable(value))).containsExactly("not storable");
    }

    @ParameterizedTest(name = "D8: storable text \"{0}\"")
    @ValueSource(strings = {"Feature: A\n\tGiven x\r\n", "😀 a pair", "\u0007 bell is storable in a source", ""})
    void otherTextIsStorable(String value) {
        assertThat(valid(new Storable(value))).isTrue();
    }

    @ParameterizedTest(name = "D19 #1: control character U+{0}")
    @ValueSource(strings = {"0000", "0007", "0009", "000A", "001F", "007F", "0085", "009F"})
    void controlCharactersAreRejected(String hex) {
        assertThat(valid(new NoControls("a" + Character.toString(Integer.parseInt(hex, 16)) + "b"))).isFalse();
    }

    @Test
    @DisplayName("D19 #1: U+00A0 and U+200B are not control characters")
    void blanksAreNotControlCharacters() {
        assertThat(valid(new NoControls("a\u00A0b\u200Bc"))).isTrue();
    }

    @Test
    @DisplayName("D19 #9: the control rule applies to the trimmed value, so a trailing line break is fine")
    void theTrimmedControlRuleIgnoresOuterBlanks() {
        assertThat(valid(new TrimmedNoControls(" first draft\n"))).isTrue();
        assertThat(valid(new TrimmedNoControls("\u200B\u00A0first draft\u2060\uFEFF"))).isTrue();
        assertThat(valid(new TrimmedNoControls("first\ndraft"))).isFalse();
    }

    @Test
    @DisplayName("D19 #9: Unicode-blank trimming removes rule T2's set from both ends only")
    void unicodeBlankTrimming() {
        assertThat(UnicodeBlank.trim("\u200B\u00A0 a b \u2060\uFEFF")).isEqualTo("a b");
        assertThat(UnicodeBlank.trim("\u200C\u200D\u2007")).isEmpty();
        assertThat(UnicodeBlank.isBlank("\u3000\u200B")).isTrue();
        assertThat(UnicodeBlank.isBlank("\u200Ba")).isFalse();
    }

    @ParameterizedTest(name = "AC-1.16 D10: the path \"{0}\" has an invisible part")
    @ValueSource(strings = {"\u00A0features/address.feature", "\u2060features/address.feature",
            " features/address.feature", "features/ /address.feature", "features/\u200B/address.feature",
            "features/\u00A0\u2007.feature", "features/\uFEFF.feature"})
    void invisiblePathPartsAreRejected(String path) {
        assertThat(messages(new Path(path))).containsExactly("invisible part");
    }

    @ParameterizedTest(name = "D10: the path \"{0}\" has no invisible part")
    @ValueSource(strings = {"features/address.feature", "features/my address.feature",
            "features/my\u00A0address.feature", "features//address.feature", "a.feature"})
    void visiblePathPartsAreAccepted(String path) {
        assertThat(valid(new Path(path))).isTrue();
    }

    @Test
    @DisplayName("G12: no message contains the value")
    void noMessageContainsTheValue() {
        Set<ConstraintViolation<Limited>> violations = VALIDATOR.validate(new Limited("secret-value-QA"));

        assertThat(violations).extracting(ConstraintViolation::getMessage).noneMatch(m -> m.contains("secret"));
    }

    private static boolean valid(Object bean) {
        return VALIDATOR.validate(bean).isEmpty();
    }

    private static List<String> messages(Object bean) {
        return VALIDATOR.validate(bean).stream().map(ConstraintViolation::getMessage).toList();
    }
}
