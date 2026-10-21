package org.scenarioservice.qa;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.scenarioservice.support.AccessCheckStub;
import org.scenarioservice.support.FeatureData;
import org.scenarioservice.support.GherkinSources;
import org.scenarioservice.support.ScenarioServiceIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.scenarioservice.support.FeatureCalls.create;
import static org.scenarioservice.support.FeatureCalls.features;
import static org.scenarioservice.support.FeatureCalls.fields;
import static org.scenarioservice.support.FeatureCalls.list;
import static org.scenarioservice.support.FeatureCalls.perform;
import static org.scenarioservice.support.FeatureCalls.save;
import static org.scenarioservice.support.FeatureCalls.view;
import static org.scenarioservice.support.ProblemAssertions.assertProblem;
import static org.scenarioservice.support.ProblemAssertions.body;
import static org.scenarioservice.support.ScenarioFixture.CHECKOUT;
import static org.scenarioservice.support.ScenarioFixture.GUS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * QA (adversarial, D7, D8, D13, AC-1.4, AC-1.11, AC-1.12, AC-2.6, AC-4.x): odd Gherkin. Every text gets 201 or 422,
 * never a 5xx; accepted text comes back byte for byte and can be viewed and compiled; parse errors always have a
 * 1-based line and column. Dialect keywords below were read from gherkin 28.0.0's dialect list (QA scratch run, not
 * imported here: only the {@code gherkin} package uses {@code io.cucumber}, P14).
 */
@ScenarioServiceIntegrationTest
class QaGherkinEdgeCasesTest {

    private static final int MAX_SOURCE_CODE_POINTS = 1_000_000;

    private final AccessCheckStub stub = AccessCheckStub.get();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FeatureData data;

    @Autowired
    private JsonMapper jsonMapper;

    @BeforeEach
    void reset() {
        data.reset();
        stub.reset();
    }

    static Stream<Arguments> dialects() {
        return Stream.of(
                Arguments.of("en-tx", "This ain\u2019t my first rodeo", "Lemme tell y'all a story", "All hat and no cattle",
                        "Serious as a snake bite", "Now that's a story longer than a cattle drive in July",
                        "Fixin' to ", "Quick out of the chute ", "There\u2019s no tree but bears some fruit "),
                Arguments.of("em", "\uD83D\uDCDA", "\uD83D\uDCA4", "\uD83E\uDD52", "\uD83D\uDCD6", "\uD83D\uDCD3",
                        "\uD83D\uDE10", "\uD83C\uDFAC", "\uD83D\uDE4F"),
                Arguments.of("ar", "\u062E\u0627\u0635\u064A\u0629", "\u0627\u0644\u062E\u0644\u0641\u064A\u0629",
                        "\u0645\u062B\u0627\u0644", "\u0633\u064A\u0646\u0627\u0631\u064A\u0648 \u0645\u062E\u0637\u0637",
                        "\u0627\u0645\u062B\u0644\u0629", "\u0628\u0641\u0631\u0636 ", "\u0645\u062A\u0649 ",
                        "\u0627\u0630\u0627\u064B "),
                Arguments.of("he", "\u05EA\u05DB\u05D5\u05E0\u05D4", "\u05E8\u05E7\u05E2", "\u05D3\u05D5\u05D2\u05DE\u05D0",
                        "\u05EA\u05D1\u05E0\u05D9\u05EA \u05EA\u05E8\u05D7\u05D9\u05E9",
                        "\u05D3\u05D5\u05D2\u05DE\u05D0\u05D5\u05EA", "\u05D1\u05D4\u05D9\u05E0\u05EA\u05DF ",
                        "\u05DB\u05D0\u05E9\u05E8 ", "\u05D0\u05D6 "),
                Arguments.of("zh-CN", "\u529F\u80FD", "\u80CC\u666F", "\u573A\u666F", "\u573A\u666F\u5927\u7EB2",
                        "\u4F8B\u5B50", "\u5047\u5982", "\u5F53", "\u90A3\u4E48"),
                Arguments.of("ja", "\u30D5\u30A3\u30FC\u30C1\u30E3", "\u80CC\u666F", "\u30B7\u30CA\u30EA\u30AA",
                        "\u30B7\u30CA\u30EA\u30AA\u30A2\u30A6\u30C8\u30E9\u30A4\u30F3", "\u4F8B", "\u524D\u63D0",
                        "\u3082\u3057", "\u306A\u3089\u3070"),
                Arguments.of("ru", "\u0424\u0443\u043D\u043A\u0446\u0438\u044F",
                        "\u041F\u0440\u0435\u0434\u044B\u0441\u0442\u043E\u0440\u0438\u044F",
                        "\u041F\u0440\u0438\u043C\u0435\u0440",
                        "\u0421\u0442\u0440\u0443\u043A\u0442\u0443\u0440\u0430 \u0441\u0446\u0435\u043D\u0430\u0440\u0438\u044F",
                        "\u041F\u0440\u0438\u043C\u0435\u0440\u044B", "\u0414\u043E\u043F\u0443\u0441\u0442\u0438\u043C ",
                        "\u041A\u043E\u0433\u0434\u0430 ", "\u0422\u043E "),
                Arguments.of("de", "Funktionalit\u00E4t", "Grundlage", "Beispiel", "Szenariogrundriss", "Beispiele",
                        "Angenommen ", "Wenn ", "Dann "),
                Arguments.of("en-pirate", "Ahoy matey!", "Yo-ho-ho", "Heave to", "Shiver me timbers",
                        "Dead men tell no tales", "Gangway! ", "Blimey! ", "Let go and haul "),
                Arguments.of("tlh", "Qap", "mo'", "lut", "lut chovnatlh", "ghantoH", "ghu' noblu' ", "qaSDI' ",
                        "vaj "));
    }

    @ParameterizedTest(name = "dialect {0}")
    @MethodSource("dialects")
    @Tag("AC-1.12")
    void everyDialectIsStoredStructuredAndCompiled(String language, String feature, String background,
                                                   String scenario, String outline, String examples, String given,
                                                   String when, String then) throws Exception {
        String source = "# language: " + language + "\n" + feature + ": Name " + language + "\n"
                + "  " + background + ":\n    " + given + "base\n"
                + "  " + scenario + ": one\n    " + when + "act\n    " + then + "check\n"
                + "  " + outline + ": two <x>\n    " + given + "<x>\n"
                + "    " + examples + ":\n      | x |\n      | 1 |\n      | 2 |\n";

        String created = createdBody("features/" + language + ".feature", source);

        assertThat(JsonPath.<String>read(created, "$.language")).isEqualTo(language);
        assertThat(JsonPath.<String>read(created, "$.name")).isEqualTo("Name " + language);
        assertThat(JsonPath.<List<String>>read(created, "$.children[*].kind"))
                .containsExactly("BACKGROUND", "SCENARIO", "SCENARIO_OUTLINE");
        assertThat(JsonPath.<List<String>>read(created, "$.children[*].keyword"))
                .containsExactly(background, scenario, outline);
        assertThat(JsonPath.<List<String>>read(created, "$.children[1].steps[*].keyword"))
                .containsExactly(when.stripTrailing(), then.stripTrailing());
        assertThat(JsonPath.<List<String>>read(created, "$.children[*].steps[*].keywordType"))
                .containsExactly("GIVEN", "WHEN", "THEN", "GIVEN");
        assertThat(JsonPath.<String>read(created, "$.children[2].examples[0].keyword")).isEqualTo(examples);
        String testCases = testCases(JsonPath.read(created, "$.id"));
        assertThat(JsonPath.<List<String>>read(testCases, "$[*].name")).containsExactly("one", "two 1", "two 2");
        assertThat(JsonPath.<List<String>>read(testCases, "$[0].steps[*].keyword"))
                .containsExactly(given.stripTrailing(), when.stripTrailing(), then.stripTrailing());
    }

    @ParameterizedTest(name = "line endings: {0}")
    @ValueSource(strings = {"CRLF", "CR only", "mixed", "no final newline", "BOM first", "tabs for indentation"})
    @Tag("AC-2.6")
    void lineEndingsAndInvisiblePrefixesRoundTrip(String variant) throws Exception {
        String source = switch (variant) {
            case "CRLF" -> GherkinSources.PAYMENT.replace("\n", "\r\n");
            case "CR only" -> GherkinSources.PAYMENT.replace("\n", "\r");
            case "mixed" -> GherkinSources.PAYMENT.replaceFirst("\n", "\r\n").replaceFirst("\n\n", "\r\r");
            case "no final newline" -> GherkinSources.PAYMENT.stripTrailing();
            case "BOM first" -> "\uFEFF" + GherkinSources.PAYMENT;
            default -> GherkinSources.PAYMENT.replace("  ", "\t");
        };

        String created = createdBody("features/endings.feature", source);

        assertThat(JsonPath.<String>read(created, "$.source")).isEqualTo(source);
        String id = JsonPath.read(created, "$.id");
        assertThat(JsonPath.<String>read(body(view(mockMvc, GUS, CHECKOUT, UUID.fromString(id))), "$.source"))
                .isEqualTo(source);
        assertThat(JsonPath.<String>read(created, "$.name")).isEqualTo("Payment");
        assertThat(JsonPath.<List<Integer>>read(created, "$.children[*].line")).containsExactly(5, 12, 19);
        assertThat(JsonPath.<List<Object>>read(testCases(id), "$")).hasSize(4);
    }

    @Test
    @DisplayName("Unicode in names, steps, tags and cells is kept exactly (4-byte, RTL, combining, U+2028)")
    void unicodeIsKeptExactly() throws Exception {
        String name = "Zahlung \uD83D\uDCB3 \u05E9\u05DC\u05D5\u05DD e\u0301 \u202Eevil";
        String source = "@t\u00E4g @\uD83D\uDE00\nFeature: " + name + "\n  Scenario: S \u2028 x\n"
                + "    Given \u4E2D\u6587 \uD800\uDF48\n      | \u00E9 | \uD83D\uDE00 |\n";

        String created = createdBody("features/unicode.feature", source);

        assertThat(JsonPath.<String>read(created, "$.name")).isEqualTo(name);
        assertThat(JsonPath.<List<String>>read(created, "$.tags")).containsExactly("@t\u00E4g", "@\uD83D\uDE00");
        assertThat(JsonPath.<String>read(created, "$.children[0].name")).isEqualTo("S \u2028 x");
        assertThat(JsonPath.<String>read(created, "$.children[0].steps[0].text")).isEqualTo("\u4E2D\u6587 \uD800\uDF48");
        assertThat(JsonPath.<List<List<String>>>read(created, "$.children[0].steps[0].dataTable"))
                .containsExactly(List.of("\u00E9", "\uD83D\uDE00"));
    }

    @Test
    @DisplayName("D8 named deviation: control characters other than U+0000 are allowed in source and round-trip")
    void controlCharactersInTheSourceAreStored() throws Exception {
        String source = "Feature: Controls\n  Scenario: S\n    Given a bell \u0007 and a NEL \u0085 here\n"
                + "      \"\"\"\n      \u0001\u001F\u007F\u009F\n      \"\"\"\n";

        MvcResult result = create(mockMvc, GUS, CHECKOUT, "features/controls.feature", source);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        // Read with Jackson: JsonPath's json-smart parser drops U+007F from strings (QA finding about the tool)
        assertThat(jsonMapper.readTree(result.getResponse().getContentAsByteArray()).get("source").asString())
                .isEqualTo(source);
        assertThat(jsonMapper.readTree(result.getResponse().getContentAsByteArray()).at("/children/0/steps/0/docString")
                .get("content").asString()).isEqualTo("\u0001\u001F\u007F\u009F");
    }

    @Test
    @DisplayName("Escaped table cells (\\|, \\\\, \\n) are unescaped in the structure and in test cases")
    void escapedCellsAreUnescaped() throws Exception {
        String source = "Feature: Escapes\n  Scenario Outline: O <v>\n    Given a table\n"
                + "      | a \\| b | c\\\\d | e\\nf |\n    Examples:\n      | v |\n      | x \\| y |\n";

        String created = createdBody("features/escapes.feature", source);

        assertThat(JsonPath.<List<List<String>>>read(created, "$.children[0].steps[0].dataTable"))
                .containsExactly(List.of("a | b", "c\\d", "e\nf"));
        assertThat(JsonPath.<List<List<String>>>read(created, "$.children[0].examples[0].rows[*].cells"))
                .containsExactly(List.of("x | y"));
        assertThat(JsonPath.<List<String>>read(testCases(JsonPath.read(created, "$.id")), "$[*].name"))
                .containsExactly("O x | y");
    }

    @ParameterizedTest(name = "malformed: {0}")
    @ValueSource(strings = {"row without a closing pipe", "examples row with a missing cell", "table after Feature",
            "unterminated doc string", "two Backgrounds", "two Features", "Examples without an outline",
            "a tag on a step", "a Rule inside a Rule's scenario"})
    @Tag("AC-1.4")
    void malformedTextIsNever5xx(String variant) throws Exception {
        String source = switch (variant) {
            case "row without a closing pipe" -> "Feature: T\n  Scenario: S\n    Given t\n      | a | b\n";
            case "examples row with a missing cell" ->
                    "Feature: T\n  Scenario Outline: S\n    Given <a>\n    Examples:\n      | a | b |\n      | 1 |\n";
            case "table after Feature" -> "Feature: T\n  | a |\n";
            case "unterminated doc string" -> "Feature: T\n  Scenario: S\n    Given d\n      \"\"\"\n      text\n";
            case "two Backgrounds" -> "Feature: T\n  Background:\n    Given a\n  Background:\n    Given b\n";
            case "two Features" -> "Feature: A\nFeature: B\n";
            case "Examples without an outline" -> "Feature: T\n  Examples:\n    | a |\n";
            case "a tag on a step" -> "Feature: T\n  Scenario: S\n    @tag\n    Given a\n";
            default -> "Feature: T\n  Rule: R\n    Scenario: S\n      Rule: Inner\n";
        };

        MvcResult result = create(mockMvc, GUS, CHECKOUT, "features/malformed.feature", source);

        int status = result.getResponse().getStatus();
        assertThat(status).as(body(result)).isIn(201, 422);
        if (status == 422) {
            Map<String, Object> problem = assertProblem(result, 422, "GHERKIN_INVALID");
            List<Map<String, Object>> errors = parseErrors(problem);
            assertThat(errors).isNotEmpty().allSatisfy(error -> {
                assertThat((Integer) error.get("line")).isPositive();
                assertThat((Integer) error.get("column")).isPositive();
                assertThat((String) error.get("message")).isNotBlank();
            });
            assertThat(data.stored()).isEqualTo("0 features, 0 versions, 0 tags");
        } else {
            assertThat(JsonPath.<String>read(body(result), "$.source")).isEqualTo(source);
        }
    }

    @ParameterizedTest(name = "AC-1.19 pragma {0}: 422 with one entry at 1:1, nothing stored")
    @ValueSource(strings = {"# encoding: foo", "# encoding: -", "# encoding: ISO-8859-1", "# encoding: latin1",
            "# encoding: UTF-16", "#encoding:ascii", "  # ENCODING : windows-1252", "# language: fr\n# encoding: foo",
            "\n\n# encoding: foo", "\uFEFF# encoding: foo"})
    @Tag("AC-1.19")
    void nonUtf8PragmasAreRejected(String pragma) throws Exception {
        MvcResult result = create(mockMvc, GUS, CHECKOUT, "features/e.feature", pragma + "\nFeature: Caf\u00E9\n");

        Map<String, Object> problem = assertProblem(result, 422, "GHERKIN_INVALID");
        assertThat(parseErrors(problem)).singleElement().satisfies(error -> {
            assertThat(error).containsEntry("line", 1).containsEntry("column", 1);
            assertThat((String) error.get("message")).isEqualTo("Only UTF-8 encoding is supported")
                    .doesNotContainIgnoringCase("foo").doesNotContainIgnoringCase("latin");
        });
        assertThat(data.stored()).isEqualTo("0 features, 0 versions, 0 tags");
    }

    @ParameterizedTest(name = "AC-1.19 pragma {0}: 201 and the name is Caf\u00E9")
    @ValueSource(strings = {"# encoding: UTF-8", "# encoding: utf-8", "# encoding: UTF8", "# Encoding: utf8",
            "Feature: Before\n# encoding: foo"})
    @Tag("AC-1.19")
    void utf8PragmasAndLatePragmasAreAccepted(String pragma) throws Exception {
        String source = pragma.startsWith("Feature") ? pragma.replace("Feature: Before\n", "Feature: Caf\u00E9\n")
                : pragma + "\nFeature: Caf\u00E9\n";

        String created = createdBody("features/u.feature", source);

        assertThat(JsonPath.<String>read(created, "$.name")).isEqualTo("Caf\u00E9");
    }

    @Test
    @Tag("AC-1.4")
    @DisplayName("An error at the end of the text (unterminated doc string) is 422 with line and column >= 1")
    void anErrorAtTheEndHasAPosition() throws Exception {
        MvcResult result = create(mockMvc, GUS, CHECKOUT, "features/eof.feature",
                "Feature: T\n  Scenario: S\n    Given d\n      \"\"\"\n      text\n");

        List<Map<String, Object>> errors = parseErrors(assertProblem(result, 422, "GHERKIN_INVALID"));
        assertThat(errors).isNotEmpty().allSatisfy(error -> assertThat((Integer) error.get("column")).isPositive());
    }

    @Test
    @Tag("AC-4.1")
    @DisplayName("D13: Rule Backgrounds and tags reach the test cases (Feature, then Rule Background; Feature, Rule tags)")
    void ruleBackgroundsAndTagsReachTestCases() throws Exception {
        String source = "@f\nFeature: R\n  Background:\n    Given feature bg\n"
                + "  @r\n  Rule: One\n    Background:\n      Given rule bg\n"
                + "    @s @f\n    Scenario: S\n      Then done\n";

        String id = JsonPath.read(createdBody("features/rule.feature", source), "$.id");
        String testCases = testCases(id);

        assertThat(JsonPath.<List<String>>read(testCases, "$[0].tags")).containsExactly("@f", "@r", "@s");
        assertThat(JsonPath.<List<String>>read(testCases, "$[0].steps[*].text"))
                .containsExactly("feature bg", "rule bg", "done");
    }

    @Test
    @Tag("AC-4.2")
    @DisplayName("D13: placeholders are replaced in data tables and doc strings; unknown placeholders stay")
    void placeholdersInArgumentsAreReplaced() throws Exception {
        String source = "Feature: P\n  Scenario Outline: <who> and <nobody>\n    Given a table\n      | <who> | x |\n"
                + "    Then a doc\n      \"\"\"<who>\n      Hi <who>, <nobody>\n      \"\"\"\n"
                + "    Examples:\n      | who |\n      | Ana |\n";

        String testCases = testCases(JsonPath.read(createdBody("features/placeholders.feature", source), "$.id"));

        assertThat(JsonPath.<String>read(testCases, "$[0].name")).isEqualTo("Ana and <nobody>");
        assertThat(JsonPath.<List<List<String>>>read(testCases, "$[0].steps[0].dataTable"))
                .containsExactly(List.of("Ana", "x"));
        assertThat(JsonPath.<String>read(testCases, "$[0].steps[1].docString.content")).isEqualTo("Hi Ana, <nobody>");
        assertThat(JsonPath.<String>read(testCases, "$[0].steps[1].docString.mediaType")).isEqualTo("Ana");
    }

    @Test
    @DisplayName("D8: a doc string media type of 1,000 characters is stored (doc_string_media_type is text)")
    void aLongMediaTypeIsStored() throws Exception {
        String mediaType = "m".repeat(1000);
        String source = "Feature: M\n  Scenario: S\n    Given d\n      ```" + mediaType + "\n      x\n      ```\n";

        String created = createdBody("features/media.feature", source);

        assertThat(JsonPath.<String>read(created, "$.children[0].steps[0].docString.mediaType")).isEqualTo(mediaType);
    }

    @Test
    @Tag("AC-1.7")
    @DisplayName("D8: a source of exactly 1,000,000 code points of 4-byte characters is stored; one more is 400")
    void theSourceLimitCountsCodePoints() throws Exception {
        String prefix = "Feature: Big\n  Scenario: S\n    Given a doc\n      \"\"\"\n      ";
        String suffix = "\n      \"\"\"\n";
        int fill = MAX_SOURCE_CODE_POINTS - prefix.length() - suffix.length();
        String source = prefix + "\uD83D\uDE00".repeat(fill) + suffix;
        assertThat(source.codePointCount(0, source.length())).isEqualTo(MAX_SOURCE_CODE_POINTS);

        long start = System.nanoTime();
        MvcResult result = create(mockMvc, GUS, CHECKOUT, "features/big.feature", source);
        Duration createTook = Duration.ofNanos(System.nanoTime() - start);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        UUID id = UUID.fromString(JsonPath.read(body(result), "$.id"));
        start = System.nanoTime();
        assertThat(JsonPath.<String>read(body(view(mockMvc, GUS, CHECKOUT, id)), "$.source")).isEqualTo(source);
        assertThat(list(mockMvc, GUS, CHECKOUT).getResponse().getStatus()).isEqualTo(200);
        Duration readTook = Duration.ofNanos(System.nanoTime() - start);
        System.out.println("QA-MEASURE max-size source: create " + createTook.toMillis() + " ms, view+list "
                + readTook.toMillis() + " ms");

        Map<String, Object> tooLong = fields("path", "features/bigger.feature", "source",
                prefix + "\uD83D\uDE00".repeat(fill + 1) + suffix);
        Map<String, Object> problem = assertProblem(create(mockMvc, GUS, CHECKOUT, tooLong), 400,
                "VALIDATION_FAILED");
        assertThat(problem.get("errors").toString()).contains("source");
    }

    @Test
    @DisplayName("An outline with 2,000 example rows: 2,000 test cases with distinct ids; one changed row keeps 1,999")
    void anOutlineWithManyRows() throws Exception {
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < 2000; i++) {
            rows.append("      | ").append(i).append(" |\n");
        }
        String source = "Feature: Many\n  Scenario Outline: Row <n>\n    Given <n>\n    Examples:\n      | n |\n" + rows;

        long start = System.nanoTime();
        String created = createdBody("features/many.feature", source);
        Duration createTook = Duration.ofNanos(System.nanoTime() - start);
        UUID id = UUID.fromString(JsonPath.read(created, "$.id"));
        List<String> before = JsonPath.read(testCases(id.toString()), "$[*].id");
        assertThat(new HashSet<>(before)).hasSize(2000);

        String changed = source.replace("      | 1999 |\n", "      | changed |\n");
        start = System.nanoTime();
        MvcResult saved = save(mockMvc, GUS, CHECKOUT, id, fields("path", "features/many.feature", "source", changed,
                "baseVersion", 1));
        Duration saveTook = Duration.ofNanos(System.nanoTime() - start);
        assertThat(saved.getResponse().getStatus()).isEqualTo(200);
        List<String> after = JsonPath.read(testCases(id.toString()), "$[*].id");
        assertThat(after.subList(0, 1999)).isEqualTo(before.subList(0, 1999));
        assertThat(after.get(1999)).isNotIn(before);
        System.out.println("QA-MEASURE 2,000-row outline: create " + createTook.toMillis() + " ms, save "
                + saveTook.toMillis() + " ms");
    }

    @Test
    @DisplayName("A feature with 2,000 scenarios: created, viewed and compiled; an edit keeps every scenario id")
    void aFeatureWithManyScenarios() throws Exception {
        StringBuilder text = new StringBuilder("Feature: Wide\n");
        for (int i = 0; i < 2000; i++) {
            text.append("  Scenario: S").append(i).append("\n    Given step ").append(i).append("\n");
        }
        String source = text.toString();

        long start = System.nanoTime();
        String created = createdBody("features/wide.feature", source);
        Duration createTook = Duration.ofNanos(System.nanoTime() - start);
        UUID id = UUID.fromString(JsonPath.read(created, "$.id"));
        List<String> before = JsonPath.read(created, "$.children[*].id");

        start = System.nanoTime();
        MvcResult saved = save(mockMvc, GUS, CHECKOUT, id, fields("path", "features/wide.feature", "source",
                source.replace("Given step 7\n", "Given step seven\n"), "baseVersion", 1));
        Duration saveTook = Duration.ofNanos(System.nanoTime() - start);

        assertThat(saved.getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonPath.<List<String>>read(body(saved), "$.children[*].id")).isEqualTo(before);
        assertThat(JsonPath.<List<Object>>read(testCases(id.toString()), "$")).hasSize(2000);
        System.out.println("QA-MEASURE 2,000 scenarios: create " + createTook.toMillis() + " ms, save "
                + saveTook.toMillis() + " ms");
    }

    @Test
    @Tag("AC-1.11")
    @DisplayName("AC-1.11 (last line, not asserted elsewhere): an empty Feature has no test cases")
    void anEmptyFeatureHasNoTestCases() throws Exception {
        String created = createdBody("features/empty.feature", "Feature:\n");

        assertThat(JsonPath.<List<Object>>read(testCases(JsonPath.read(created, "$.id")), "$")).isEmpty();
    }

    @Test
    @DisplayName("Accepted deviation: a Scenario with Examples is a SCENARIO_OUTLINE and yields one test case per row")
    void aScenarioWithExamplesIsAnOutline() throws Exception {
        String source = "Feature: E\n  Scenario: Pay <n>\n    Given <n>\n    Examples:\n      | n |\n      | 1 |\n"
                + "      | 2 |\n";

        String created = createdBody("features/scenario-examples.feature", source);

        assertThat(JsonPath.<String>read(created, "$.children[0].kind")).isEqualTo("SCENARIO_OUTLINE");
        assertThat(JsonPath.<String>read(created, "$.children[0].keyword")).isEqualTo("Scenario");
        assertThat(JsonPath.<List<String>>read(testCases(JsonPath.read(created, "$.id")), "$[*].type"))
                .containsExactly("EXAMPLE_ROW", "EXAMPLE_ROW");
    }

    private String createdBody(String path, String source) throws Exception {
        MvcResult result = create(mockMvc, GUS, CHECKOUT, path, source);
        assertThat(result.getResponse().getStatus()).as(body(result)).isEqualTo(201);
        return body(result);
    }

    private String testCases(String featureId) throws Exception {
        MvcResult result = perform(mockMvc, GUS, get(features(CHECKOUT) + "/" + featureId + "/test-cases"));
        assertThat(result.getResponse().getStatus()).as(body(result)).isEqualTo(200);
        return body(result);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parseErrors(Map<String, Object> problem) {
        return (List<Map<String, Object>>) problem.get("parseErrors");
    }
}
