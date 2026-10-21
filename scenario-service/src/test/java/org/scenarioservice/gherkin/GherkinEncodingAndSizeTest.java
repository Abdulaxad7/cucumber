package org.scenarioservice.gherkin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.scenarioservice.support.GherkinSources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * D21 #1 and #2 (unit, fix round 1): only UTF-8 encoding pragmas are accepted, as one parse error at 1:1, and no
 * charset exception escapes the adapter; and a parsed feature counts its elements as D21 defines them.
 */
class GherkinEncodingAndSizeTest {

    private final GherkinAdapter adapter = new GherkinAdapter();

    @ParameterizedTest(name = "AC-1.19 the pragma \"{0}\" is rejected at 1:1")
    @ValueSource(strings = {"# encoding: foo", "# encoding: -", "# encoding: ISO-8859-1", "# ENCODING: latin1",
            "#encoding:UTF-16", "  # Encoding : us-ascii", "\uFEFF# encoding: foo", "# encoding: utf-8-x"})
    @Tag("AC-1.19")
    void aNonUtf8PragmaIsOneParseErrorAtTheStart(String pragma) {
        GherkinParseResult result = adapter.parse(pragma + "\nFeature: Caf\u00E9\n");

        assertThat(result).isInstanceOf(GherkinParseResult.Invalid.class);
        assertThat(((GherkinParseResult.Invalid) result).errors())
                .containsExactly(new GherkinParseError(1, 1, "Only UTF-8 encoding is supported"));
    }

    @Test
    @Tag("AC-1.19")
    @DisplayName("AC-1.19 a non-UTF-8 pragma after other leading comments is found too, as the parser would")
    void aPragmaAfterLeadingCommentsIsFound() {
        GherkinParseResult result = adapter.parse("# a comment\n\n# encoding: foo\nFeature: F\n");

        assertThat(((GherkinParseResult.Invalid) result).errors())
                .containsExactly(new GherkinParseError(1, 1, "Only UTF-8 encoding is supported"));
    }

    @ParameterizedTest(name = "AC-1.19 the pragma \"{0}\" is UTF-8 and accepted")
    @ValueSource(strings = {"# encoding: UTF-8", "# encoding: utf-8", "# encoding: UTF8",
            "# encoding: unicode-1-1-utf-8"})
    @Tag("AC-1.19")
    void aUtf8PragmaIsAccepted(String pragma) {
        GherkinParseResult result = adapter.parse(pragma + "\nFeature: Caf\u00E9\n");

        assertThat(result).isInstanceOf(GherkinParseResult.Valid.class);
        assertThat(((GherkinParseResult.Valid) result).feature().name()).isEqualTo("Caf\u00E9");
    }

    @Test
    @DisplayName("D21 #1: a pragma-like comment after the Feature line is just a comment")
    void aPragmaAfterTheFeatureLineIsAComment() {
        assertThat(adapter.parse("Feature: F\n# encoding: foo\n")).isInstanceOf(GherkinParseResult.Valid.class);
        assertThatCode(() -> adapter.testCases("Feature: F\n# encoding: foo\n  Scenario: S\n    Given x\n"))
                .doesNotThrowAnyException();
    }

    @Test
    @Tag("AC-1.20")
    @DisplayName("D21 #2: the Payment source has 17 elements")
    void thePaymentElementCount() {
        // 1 rule, 3 scenarios (Background, Scenario, Outline), 2 Examples blocks, 5 steps, 3 rows, 3 tag assignments
        assertThat(feature(GherkinSources.PAYMENT).elementCount()).isEqualTo(17);
    }

    @Test
    @Tag("AC-1.20")
    @DisplayName("D21 #2: data-table cells and doc-string text don't count; a repeated tag counts once")
    void argumentsAndRepeatedTagsCountOnce() {
        String source = "@a @a\nFeature: F\n  Scenario: S\n    Given a table\n      | a | b |\n      | 1 | 2 |\n"
                + "    Then a doc\n      \"\"\"\n      x\n      y\n      \"\"\"\n";

        // 1 scenario, 2 steps, 1 tag assignment
        assertThat(feature(source).elementCount()).isEqualTo(4);
    }

    private ParsedFeature feature(String source) {
        return ((GherkinParseResult.Valid) adapter.parse(source)).feature();
    }
}
