package org.scenarioservice.gherkin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.scenarioservice.enums.ScenarioKind;
import org.scenarioservice.enums.StepKeyword;
import org.scenarioservice.support.GherkinSources;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * T6 (unit, no Spring context): the Gherkin adapter turns text into this service's model with the official parser
 * (D7), applies D8's validity rule with 1-based, sorted parse errors, and compiles test cases as Cucumber's pickles
 * (D13), mapped to scenario and example-row positions.
 */
class GherkinAdapterTest {

    private final GherkinAdapter adapter = new GherkinAdapter();

    @Test
    @Tag("AC-1.2")
    @DisplayName("AC-1.2 The Payment source's structure")
    void thePaymentStructure() {
        ParsedFeature feature = valid(GherkinSources.PAYMENT);

        assertThat(feature.language()).isEqualTo("en");
        assertThat(feature.keyword()).isEqualTo("Feature");
        assertThat(feature.name()).isEqualTo("Payment");
        assertThat(feature.description().trim()).isEqualTo("Customers pay for their basket.");
        assertThat(feature.line()).isEqualTo(2);
        assertThat(feature.tags()).containsExactly("@checkout");
        assertThat(feature.children()).hasSize(3);

        ParsedScenario background = (ParsedScenario) feature.children().get(0);
        assertThat(background.kind()).isEqualTo(ScenarioKind.BACKGROUND);
        assertThat(background.keyword()).isEqualTo("Background");
        assertThat(background.name()).isEmpty();
        assertThat(background.tags()).isEmpty();
        assertThat(background.steps()).singleElement().satisfies(step -> {
            assertThat(step.keyword()).isEqualTo("Given");
            assertThat(step.keywordType()).isEqualTo(StepKeyword.GIVEN);
            assertThat(step.text()).isEqualTo("a basket with these items:");
            assertThat(step.dataTable()).containsExactly(List.of("item", "price"), List.of("Mug", "8.00"),
                    List.of("Shirt", "20.00"));
            assertThat(step.docString()).isNull();
        });

        ParsedScenario card = (ParsedScenario) feature.children().get(1);
        assertThat(card.kind()).isEqualTo(ScenarioKind.SCENARIO);
        assertThat(card.keyword()).isEqualTo("Scenario");
        assertThat(card.name()).isEqualTo("Pay by card");
        assertThat(card.line()).isEqualTo(12);
        assertThat(card.tags()).containsExactly("@smoke");
        assertThat(card.steps()).extracting(ParsedStep::keyword, ParsedStep::keywordType, ParsedStep::text)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("When", StepKeyword.WHEN,
                                "the customer pays by card"),
                        org.assertj.core.groups.Tuple.tuple("Then", StepKeyword.THEN, "the receipt is:"));
        assertThat(card.steps().get(0).dataTable()).isNull();
        assertThat(card.steps().get(0).docString()).isNull();
        assertThat(card.steps().get(1).docString()).isEqualTo(new ParsedDocString("Total: 28.00", "text"));
        assertThat(card.examples()).isEmpty();

        ParsedRule rule = (ParsedRule) feature.children().get(2);
        assertThat(rule.keyword()).isEqualTo("Rule");
        assertThat(rule.name()).isEqualTo("Vouchers");
        assertThat(rule.line()).isEqualTo(19);
        assertThat(rule.children()).singleElement().satisfies(outline -> {
            assertThat(outline.kind()).isEqualTo(ScenarioKind.SCENARIO_OUTLINE);
            assertThat(outline.keyword()).isEqualTo("Scenario Outline");
            assertThat(outline.name()).isEqualTo("Pay with a voucher");
            assertThat(outline.steps()).extracting(ParsedStep::text).containsExactly(
                    "the customer pays with voucher \"<code>\"", "the result is \"<result>\"");
            assertThat(outline.examples()).hasSize(2);
            ParsedExamples valid = outline.examples().get(0);
            assertThat(valid.keyword()).isEqualTo("Examples");
            assertThat(valid.name()).isEqualTo("Valid vouchers");
            assertThat(valid.tags()).containsExactly("@regression");
            assertThat(valid.header()).containsExactly("code", "result");
            assertThat(valid.rows()).extracting(ParsedRow::line).containsExactly(28, 29);
            assertThat(valid.rows()).extracting(ParsedRow::cells).containsExactly(List.of("SAVE10", "confirmed"),
                    List.of("SAVE20", "confirmed"));
            ParsedExamples expired = outline.examples().get(1);
            assertThat(expired.name()).isEqualTo("Expired vouchers");
            assertThat(expired.tags()).isEmpty();
            assertThat(expired.header()).containsExactly("code", "result");
            assertThat(expired.rows()).singleElement().satisfies(row -> {
                assertThat(row.line()).isEqualTo(33);
                assertThat(row.cells()).containsExactly("OLD5", "rejected");
            });
        });
        assertThat(feature.scenariosInTextOrder()).extracting(ParsedScenario::name)
                .containsExactly("", "Pay by card", "Pay with a voucher");
        assertThat(feature.rowsInTextOrder()).extracting(ParsedRow::line).containsExactly(28, 29, 33);
    }

    @Test
    @DisplayName("AC-2.2 The Address source: tags on the Feature, one scenario")
    void theAddressStructure() {
        ParsedFeature feature = valid(GherkinSources.ADDRESS);

        assertThat(feature.tags()).containsExactly("@address", "@smoke");
        assertThat(feature.children()).singleElement().isInstanceOf(ParsedScenario.class);
    }

    @Test
    @Tag("AC-1.12")
    @DisplayName("AC-1.12 A French feature is parsed with French keywords and normalised types")
    void aFrenchFeature() {
        ParsedFeature feature = valid(GherkinSources.FRENCH);

        assertThat(feature.language()).isEqualTo("fr");
        assertThat(feature.name()).isEqualTo("Paiement");
        ParsedScenario scenario = (ParsedScenario) feature.children().get(0);
        assertThat(scenario.keyword()).isEqualTo("Scénario");
        assertThat(scenario.steps()).extracting(ParsedStep::keyword, ParsedStep::keywordType, ParsedStep::text)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Soit", StepKeyword.GIVEN, "un panier"),
                        org.assertj.core.groups.Tuple.tuple("Alors", StepKeyword.THEN, "la commande est confirmée"));
    }

    @ParameterizedTest(name = "AC-1.4 {0} is reported at {1}:{2}")
    @CsvSource({"step before Feature,1,1", "inconsistent cells,5,7", "step after Examples,7,5",
            "unknown language,1,1", "no Feature,1,1"})
    @Tag("AC-1.4")
    void brokenSourcesAreReportedAtTheirLineAndColumn(String name, int line, int column) {
        String source = switch (name) {
            case "step before Feature" -> GherkinSources.STEP_BEFORE_FEATURE;
            case "inconsistent cells" -> GherkinSources.INCONSISTENT_CELLS;
            case "step after Examples" -> GherkinSources.STEP_AFTER_EXAMPLES;
            case "unknown language" -> GherkinSources.UNKNOWN_LANGUAGE;
            default -> GherkinSources.NO_FEATURE;
        };

        List<GherkinParseError> errors = invalid(source);

        assertThat(errors).anySatisfy(error -> {
            assertThat(error.line()).isEqualTo(line);
            assertThat(error.column()).isEqualTo(column);
            assertThat(error.message()).isNotBlank();
        });
    }

    @Test
    @Tag("AC-1.5")
    @DisplayName("AC-1.5 Every parse error is reported, sorted by line, then column")
    void everyParseErrorIsReportedInOrder() {
        String source = "Feature: Tables\n  Scenario: One\n    Given a table\n      | a | b |\n      | 1 |\n"
                + "  Scenario: Two\n    Given a table\n      | a | b |\n      | 1 |\n";

        List<GherkinParseError> errors = invalid(source);

        assertThat(errors).extracting(GherkinParseError::line).contains(5, 9).isSorted();
    }

    @ParameterizedTest(name = "AC-1.13 the text \"{0}\" has no Feature")
    @ValueSource(strings = {"   \n", "\n\n# only a comment\n", ""})
    @Tag("AC-1.13")
    void aTextWithoutAFeatureIsReportedAtTheStart(String source) {
        assertThat(invalid(source)).containsExactly(new GherkinParseError(1, 1, "The text has no Feature"));
    }

    @Test
    @Tag("AC-1.15")
    @DisplayName("AC-1.15 Parser errors replace the \"no Feature\" entry")
    void parserErrorsReplaceTheNoFeatureEntry() {
        List<GherkinParseError> errors = invalid("Given a step\n");

        assertThat(errors).isNotEmpty().allSatisfy(error ->
                assertThat(error.message()).isNotEqualTo("The text has no Feature"));
        assertThat(errors.get(0).line()).isEqualTo(1);
        assertThat(errors.get(0).column()).isEqualTo(1);
    }

    @Test
    @Tag("AC-1.11")
    @DisplayName("AC-1.11 A Feature without a name or children is valid")
    void anEmptyFeatureIsValid() {
        ParsedFeature feature = valid("Feature:\n");

        assertThat(feature.name()).isEmpty();
        assertThat(feature.description()).isEmpty();
        assertThat(feature.children()).isEmpty();
        assertThat(adapter.testCases("Feature:\n")).isEmpty();
    }

    @Test
    @DisplayName("D8: CRLF line endings and trailing spaces are parsed; lines count as usual")
    void crlfAndTrailingSpaces() {
        String source = "Feature: Spaces \r\n  Scenario: S  \r\n    Given a step   \r\n    Then done";

        ParsedFeature feature = valid(source);

        assertThat(feature.name()).isEqualTo("Spaces");
        ParsedScenario scenario = (ParsedScenario) feature.children().get(0);
        assertThat(scenario.name()).isEqualTo("S");
        assertThat(scenario.line()).isEqualTo(2);
        assertThat(scenario.steps()).extracting(ParsedStep::text).containsExactly("a step", "done");
        assertThat(scenario.steps()).extracting(ParsedStep::line).containsExactly(3, 4);
    }

    @Test
    @DisplayName("D19 #11: a tag repeated on one element appears once, at its first position")
    void repeatedTagsAppearOnce() {
        ParsedFeature feature = valid("@a @b @a\nFeature: F\n  @c @c\n  Scenario: S\n    Given x\n");

        assertThat(feature.tags()).containsExactly("@a", "@b");
        assertThat(((ParsedScenario) feature.children().get(0)).tags()).containsExactly("@c");
    }

    @Test
    @DisplayName("D19 #7: an Examples block without a table has an empty header and no rows")
    void examplesWithoutATable() {
        ParsedFeature feature = valid("Feature: F\n  Scenario Outline: O\n    Given <x>\n    Examples: none\n");

        ParsedExamples examples = ((ParsedScenario) feature.children().get(0)).examples().get(0);
        assertThat(examples.header()).isEmpty();
        assertThat(examples.rows()).isEmpty();
        assertThat(adapter.testCases("Feature: F\n  Scenario Outline: O\n    Given <x>\n    Examples: none\n"))
                .isEmpty();
    }

    @Test
    @DisplayName("D8: the asterisk bullet, And and But keep their own normalised types")
    void keywordTypes() {
        ParsedFeature feature = valid("Feature: F\n  Scenario: S\n    Given a\n    And b\n    But c\n    * d\n"
                + "    When e\n    Then f\n");

        assertThat(((ParsedScenario) feature.children().get(0)).steps()).extracting(ParsedStep::keywordType)
                .containsExactly(StepKeyword.GIVEN, StepKeyword.AND, StepKeyword.BUT, StepKeyword.ASTERISK,
                        StepKeyword.WHEN, StepKeyword.THEN);
    }

    @Test
    @Tag("AC-4.1")
    @DisplayName("D13: Payment compiles to 4 test cases, as Cucumber's pickles")
    void paymentTestCases() {
        List<CompiledTestCase> testCases = adapter.testCases(GherkinSources.PAYMENT);

        assertThat(testCases).extracting(CompiledTestCase::scenarioIndex).containsExactly(1, 2, 2, 2);
        assertThat(testCases).extracting(CompiledTestCase::rowIndex).containsExactly(null, 0, 1, 2);
        assertThat(testCases).extracting(CompiledTestCase::line).containsExactly(12, 28, 29, 33);
        assertThat(testCases).extracting(CompiledTestCase::name).containsExactly("Pay by card",
                "Pay with a voucher", "Pay with a voucher", "Pay with a voucher");
        assertThat(testCases).extracting(CompiledTestCase::tags).containsExactly(List.of("@checkout", "@smoke"),
                List.of("@checkout", "@regression"), List.of("@checkout", "@regression"), List.of("@checkout"));
        CompiledTestCase card = testCases.get(0);
        assertThat(card.steps()).extracting(CompiledStep::keyword, CompiledStep::text).containsExactly(
                org.assertj.core.groups.Tuple.tuple("Given", "a basket with these items:"),
                org.assertj.core.groups.Tuple.tuple("When", "the customer pays by card"),
                org.assertj.core.groups.Tuple.tuple("Then", "the receipt is:"));
        assertThat(card.steps().get(0).dataTable()).hasSize(3);
        assertThat(card.steps().get(2).docString()).isEqualTo(new ParsedDocString("Total: 28.00", "text"));
        assertThat(testCases.get(1).steps()).extracting(CompiledStep::text).containsExactly(
                "a basket with these items:", "the customer pays with voucher \"SAVE10\"",
                "the result is \"confirmed\"");
        assertThat(testCases.get(3).steps().get(2).text()).isEqualTo("the result is \"rejected\"");
    }

    @Test
    @Tag("AC-4.2")
    @DisplayName("D13: Greeting replaces placeholders in the name, steps and doc string")
    void greetingTestCases() {
        assertThat(adapter.testCases(GherkinSources.GREETING)).singleElement().satisfies(testCase -> {
            assertThat(testCase.name()).isEqualTo("Greet Ana");
            assertThat(testCase.rowIndex()).isZero();
            assertThat(testCase.tags()).isEmpty();
            assertThat(testCase.steps()).extracting(CompiledStep::text)
                    .containsExactly("the user \"Ana\"", "the greeting is:");
            assertThat(testCase.steps().get(1).docString()).isEqualTo(new ParsedDocString("Hello, Ana!", null));
        });
    }

    @Test
    @DisplayName("D13: a Rule's Background runs after the Feature's, and effective tags keep each tag once")
    void ruleBackgroundsAndEffectiveTags() {
        String source = "@a\nFeature: F\n  Background:\n    Given feature setup\n  @a @b\n  Rule: R\n"
                + "    Background:\n      Given rule setup\n    @b @c\n    Scenario: S\n      When it runs\n";

        assertThat(adapter.testCases(source)).singleElement().satisfies(testCase -> {
            assertThat(testCase.steps()).extracting(CompiledStep::text)
                    .containsExactly("feature setup", "rule setup", "it runs");
            assertThat(testCase.tags()).containsExactly("@a", "@b", "@c");
            assertThat(testCase.scenarioIndex()).isEqualTo(2);
        });
    }

    @Test
    @DisplayName("T6: compiling invalid text is a programming error")
    void compilingInvalidTextFails() {
        assertThat(catchThrowable(() -> adapter.testCases(GherkinSources.NO_FEATURE)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("comment");
    }

    @Test
    @DisplayName("P16: model records don't print user content")
    void modelRecordsDoNotPrintContent() {
        ParsedFeature feature = valid(GherkinSources.PAYMENT);

        assertThat(feature.toString() + feature.children() + invalid(GherkinSources.INCONSISTENT_CELLS)
                + adapter.testCases(GherkinSources.PAYMENT))
                .doesNotContain("Payment", "Customers", "basket", "voucher", "@checkout", "SAVE10", "inconsistent");
    }

    private ParsedFeature valid(String source) {
        GherkinParseResult result = adapter.parse(source);
        assertThat(result).isInstanceOf(GherkinParseResult.Valid.class);
        return ((GherkinParseResult.Valid) result).feature();
    }

    private List<GherkinParseError> invalid(String source) {
        GherkinParseResult result = adapter.parse(source);
        assertThat(result).isInstanceOf(GherkinParseResult.Invalid.class);
        return ((GherkinParseResult.Invalid) result).errors();
    }
}
