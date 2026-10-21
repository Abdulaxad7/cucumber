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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.scenarioservice.support.FeatureCalls.create;
import static org.scenarioservice.support.FeatureCalls.created;
import static org.scenarioservice.support.FeatureCalls.features;
import static org.scenarioservice.support.FeatureCalls.fields;
import static org.scenarioservice.support.FeatureCalls.perform;
import static org.scenarioservice.support.FeatureCalls.save;
import static org.scenarioservice.support.ProblemAssertions.assertProblem;
import static org.scenarioservice.support.ProblemAssertions.body;
import static org.scenarioservice.support.ScenarioFixture.CHECKOUT;
import static org.scenarioservice.support.ScenarioFixture.DAN;
import static org.scenarioservice.support.ScenarioFixture.GUS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * QA (adversarial, D10, D12, D19 #5, D20 #1 and #4; AC-1.6, AC-1.14, AC-1.16, AC-1.17, AC-2.2, AC-5.4, AC-5.7):
 * path rules (ICU case collisions decided by the database, invisible parts, control characters, lone surrogates, the
 * 1,024 code-point limit with 4-byte characters) and tag filter edge cases.
 */
@ScenarioServiceIntegrationTest
class QaPathAndTagRulesTest {

    private final AccessCheckStub stub = AccessCheckStub.get();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FeatureData data;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        data.reset();
        stub.reset();
    }

    static Stream<Arguments> casePairs() {
        return Stream.of(
                Arguments.of("Kelvin sign and k", "\u212A", "k"),
                Arguments.of("Angstrom sign and a-ring", "\u212B", "\u00E5"),
                Arguments.of("capital sharp s and sharp s", "\u1E9E", "\u00DF"),
                Arguments.of("sharp s and SS", "\u00DF", "SS"),
                Arguments.of("Greek final sigma", "\u039F\u0394\u039F\u03A3", "\u03BF\u03B4\u03BF\u03C2"),
                Arguments.of("Greek medial sigma at the end", "\u039F\u0394\u039F\u03A3", "\u03BF\u03B4\u03BF\u03C3"),
                Arguments.of("dotted capital I and i with dot", "\u0130", "i\u0307"),
                Arguments.of("dotless i and I", "\u0131", "I"),
                Arguments.of("titlecase dz and small dz", "\u01C5", "\u01C6"),
                Arguments.of("Roman numeral twelve", "\u216B", "\u217B"),
                Arguments.of("circled A", "\u24B6", "\u24D0"),
                Arguments.of("full-width A", "\uFF21", "\uFF41"),
                Arguments.of("Cherokee (lower case is newer)", "\u13A0", "\uAB70"),
                Arguments.of("Georgian Mtavruli (Unicode 11)", "\u1C90", "\u10D0"),
                Arguments.of("Adlam (4-byte)", "\uD83A\uDD00", "\uD83A\uDD22"),
                Arguments.of("Unicode 14 Latin", "\uA7C0", "\uA7C1"),
                Arguments.of("NFC and NFD e-acute", "\u00E9", "e\u0301"),
                Arguments.of("ff ligature and FF", "\uFB00", "FF"));
    }

    @ParameterizedTest(name = "{0}: the API agrees with the database's ICU comparison")
    @MethodSource("casePairs")
    @Tag("AC-1.17")
    void pathCollisionsAreDecidedByTheDatabase(String name, String existing, String candidate) throws Exception {
        String first = "features/" + existing + ".feature";
        String second = "features/" + candidate + ".feature";
        Boolean collide = jdbc.queryForObject("SELECT lower(CAST(? AS text) COLLATE \"und-x-icu\") = "
                + "lower(CAST(? AS text) COLLATE \"und-x-icu\")", Boolean.class, first, second);
        created(mockMvc, GUS, CHECKOUT, first, GherkinSources.ADDRESS);

        MvcResult result = create(mockMvc, GUS, CHECKOUT, second, GherkinSources.ADDRESS);

        System.out.println("QA-PAIR " + name + ": ICU collision=" + collide + ", API status="
                + result.getResponse().getStatus());
        if (Boolean.TRUE.equals(collide)) {
            assertProblem(result, 409, "FEATURE_PATH_TAKEN");
            assertThat(data.count("features")).isEqualTo(1);
        } else {
            assertThat(result.getResponse().getStatus()).as(body(result)).isEqualTo(201);
            assertThat(data.count("features")).isEqualTo(2);
        }
    }

    @Test
    @Tag("AC-1.17")
    @DisplayName("ICU lower case can lengthen a path (U+0130 -> 2 code points): a 1,024-code-point path still collides")
    void lengtheningLowerCaseStillCollides() throws Exception {
        String path = "features/" + "\u0130".repeat(1007) + ".feature";
        assertThat(path.codePointCount(0, path.length())).isEqualTo(1024);
        created(mockMvc, GUS, CHECKOUT, path, GherkinSources.ADDRESS);

        assertProblem(create(mockMvc, GUS, CHECKOUT, path, GherkinSources.ADDRESS), 409, "FEATURE_PATH_TAKEN");
    }

    @ParameterizedTest(name = "1,024 code points of {0}: stored; 1,025: 400")
    @ValueSource(strings = {"4-byte emoji", "3-byte CJK", "combining marks"})
    @Tag("AC-1.6")
    void thePathLimitCountsCodePoints(String kind) throws Exception {
        String unit = switch (kind) {
            case "4-byte emoji" -> "\uD83D\uDE00";
            case "3-byte CJK" -> "\u4E2D";
            default -> "e\u0301";
        };
        String fill = unit.repeat(1016 / unit.codePointCount(0, unit.length()));
        String exact = "features/" + fill.substring(0, fill.offsetByCodePoints(0, 1007)) + ".feature";
        String tooLong = "features/" + fill.substring(0, fill.offsetByCodePoints(0, 1008)) + ".feature";
        assertThat(exact.codePointCount(0, exact.length())).isEqualTo(1024);

        assertThat(create(mockMvc, GUS, CHECKOUT, exact, GherkinSources.ADDRESS).getResponse().getStatus())
                .isEqualTo(201);
        assertValidationError(create(mockMvc, GUS, CHECKOUT, tooLong, GherkinSources.ADDRESS), "path");
    }

    @ParameterizedTest(name = "path {0} is 400")
    @ValueSource(strings = {"tab", "line feed", "carriage return", "U+001F", "DEL", "U+0080", "U+009F",
            "lone high surrogate at the end", "lone low surrogate", "reversed surrogate pair",
            "ideographic space segment", "line separator segment", "BOM-only file name", "trailing space",
            "trailing NBSP", "leading ideographic space", "dot segment at the start", "dot as last segment",
            "upper-case suffix only", "suffix only in a folder"})
    @Tag("AC-1.16")
    void invalidPaths(String row) throws Exception {
        String path = switch (row) {
            case "tab" -> "features/a\tb.feature";
            case "line feed" -> "features/a\nb.feature";
            case "carriage return" -> "features/a\rb.feature";
            case "U+001F" -> "features/a\u001Fb.feature";
            case "DEL" -> "features/a\u007Fb.feature";
            case "U+0080" -> "features/a\u0080b.feature";
            case "U+009F" -> "features/a\u009Fb.feature";
            case "lone high surrogate at the end" -> "features/a.feature\uD83D";
            case "lone low surrogate" -> "features/\uDE00a.feature";
            case "reversed surrogate pair" -> "features/\uDE00\uD83Da.feature";
            case "ideographic space segment" -> "features/\u3000/a.feature";
            case "line separator segment" -> "features/\u2028/a.feature";
            case "BOM-only file name" -> "features/\uFEFF.feature";
            case "trailing space" -> "features/a.feature ";
            case "trailing NBSP" -> "features/a.feature\u00A0";
            case "leading ideographic space" -> "\u3000features/a.feature";
            case "dot segment at the start" -> "./a.feature";
            case "dot as last segment" -> "features/.";
            case "upper-case suffix only" -> "features/a.Feature";
            default -> "features/.feature/a.txt";
        };

        MvcResult result = create(mockMvc, GUS, CHECKOUT, path, GherkinSources.ADDRESS);

        assertValidationError(result, "path");
    }

    @ParameterizedTest(name = "path {0} is valid")
    @ValueSource(strings = {"features/my address.feature", "features/a\u00A0b.feature", ".hidden.feature",
            "features/.../a.feature", "a.feature", "features/..a.feature", "features/a%2Fb.feature",
            "features/\u00AD/a.feature", "features/a.feature.feature"})
    @Tag("AC-1.16")
    void validPathsAreStoredAsSent(String path) throws Exception {
        MvcResult result = create(mockMvc, GUS, CHECKOUT, path, GherkinSources.ADDRESS);

        assertThat(result.getResponse().getStatus()).as(body(result)).isEqualTo(201);
        assertThat(JsonPath.<String>read(body(result), "$.path")).isEqualTo(path);
    }

    @Test
    @Tag("AC-2.2")
    @DisplayName("A5: the list is ordered by path ignoring case (b between A and C), not by code point or creation")
    void theListIgnoresCaseWhenOrdering() throws Exception {
        for (String path : List.of("features/C.feature", "features/b.feature", "features/A.feature")) {
            created(mockMvc, GUS, CHECKOUT, path, GherkinSources.ADDRESS);
        }

        assertThat(JsonPath.<List<String>>read(body(perform(mockMvc, DAN, get(features(CHECKOUT)))), "$[*].path"))
                .containsExactly("features/A.feature", "features/b.feature", "features/C.feature");
    }

    @Test
    @Tag("AC-1.8")
    @DisplayName("Save: changing only the letter case of the feature's own path is allowed (the feature isn't a rival)")
    void aCaseOnlyRenameOfTheOwnPathIsAllowed() throws Exception {
        UUID id = created(mockMvc, GUS, CHECKOUT, "features/payment.feature", GherkinSources.PAYMENT);

        MvcResult result = save(mockMvc, GUS, CHECKOUT, id, fields("path", "features/Payment.feature", "source",
                GherkinSources.PAYMENT, "baseVersion", 1));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonPath.<String>read(body(result), "$.path")).isEqualTo("features/Payment.feature");
        assertThat(JsonPath.<Integer>read(body(result), "$.currentVersion")).isEqualTo(1);
    }

    @Test
    @Tag("AC-6.4")
    @DisplayName("Restore: an active feature with a case variant of the path blocks it with FEATURE_PATH_TAKEN")
    void restoreChecksCaseVariants() throws Exception {
        UUID id = created(mockMvc, GUS, CHECKOUT, "features/payment.feature", GherkinSources.PAYMENT);
        perform(mockMvc, GUS, post(features(CHECKOUT) + "/" + id + "/archive"));
        created(mockMvc, GUS, CHECKOUT, "FEATURES/PAYMENT.feature", GherkinSources.ADDRESS);

        assertProblem(perform(mockMvc, GUS, post(features(CHECKOUT) + "/" + id + "/restore")), 409,
                "FEATURE_PATH_TAKEN");
    }

    @ParameterizedTest(name = "tag filter {0}: 400 for tag")
    @ValueSource(strings = {"tag=", "tag=%40", "tag=@a&tag=", "tag=&tag=@a", "tag=@a%EF%BB%BFb", "tag=@a%E2%80%A8b",
            "tag=@a%00b", "tag=@a%C2%9Fb", "tag=@a%09b", "tag=@a%E3%80%80b", "tag=a@b", "tag=%20@a", "tag=@a%20"})
    @Tag("AC-5.7")
    void malformedTagFilters(String query) throws Exception {
        MvcResult result = perform(mockMvc, DAN, get(URI.create(features(CHECKOUT) + "?" + query)));

        assertProblem(result, 400, "VALIDATION_FAILED");
        assertThat(JsonPath.<List<String>>read(body(result), "$.errors[*].field")).containsExactly("tag");
        assertThat(stub.received()).as("validation runs before the access check (A3)").isEmpty();
    }

    @Test
    @Tag("AC-5.7")
    @DisplayName("Tag limit in code points: 1,000 emoji code points are valid, 1,001 are 400")
    void theTagLimitCountsCodePoints() throws Exception {
        String ok = "@" + "\uD83D\uDE00".repeat(999);
        String tooLong = ok + "\uD83D\uDE00";

        assertThat(perform(mockMvc, DAN, get(features(CHECKOUT)).queryParam("tag", ok)).getResponse().getStatus())
                .isEqualTo(200);
        assertProblem(perform(mockMvc, DAN, get(features(CHECKOUT)).queryParam("tag", tooLong)), 400,
                "VALIDATION_FAILED");
    }

    @Test
    @Tag("AC-5.4")
    @DisplayName("Tags the parser keeps (comma, emoji, U+200B) are matched exactly, percent-encoded or not; @@x is @x")
    void unusualTagsAreMatchedExactly() throws Exception {
        String source = "@a,b @@x @\uD83D\uDE00 @z\u200Bw\nFeature: Odd tags\n  Scenario: S\n    Given s\n";
        created(mockMvc, GUS, CHECKOUT, "features/odd.feature", source);
        created(mockMvc, GUS, CHECKOUT, "features/plain.feature", "@a @b\nFeature: Plain\n");

        assertThat(filtered("tag=@a,b")).containsExactly("features/odd.feature");
        assertThat(filtered("tag=%40a%2Cb")).containsExactly("features/odd.feature");
        assertThat(filtered("tag=@x")).as("gherkin splits tag lines on @").containsExactly("features/odd.feature");
        assertThat(filtered("tag=@@x")).isEmpty();
        assertThat(filtered("tag=@%F0%9F%98%80")).containsExactly("features/odd.feature");
        assertThat(filtered("tag=@z%E2%80%8Bw")).containsExactly("features/odd.feature");
        assertThat(filtered("tag=@zw")).isEmpty();
        assertThat(filtered("tag=@a")).containsExactly("features/plain.feature");
        assertThat(filtered("TAG=@nothing")).hasSize(2);
    }

    @Test
    @Tag("AC-5.7")
    @DisplayName("Spec gap: the parser keeps NBSP inside a tag; the catalogue lists it, but the filter must refuse it")
    void aTagWithNbspIsListedButNotFilterable() throws Exception {
        created(mockMvc, GUS, CHECKOUT, "features/nbsp.feature", "@a\u00A0b\nFeature: Nbsp\n");

        assertThat(JsonPath.<List<String>>read(body(perform(mockMvc, DAN, get("/api/projects/" + CHECKOUT + "/tags"))),
                "$[*].name")).containsExactly("@a\u00A0b");
        assertProblem(perform(mockMvc, DAN, get(URI.create(features(CHECKOUT) + "?tag=@a%C2%A0b"))), 400,
                "VALIDATION_FAILED");
    }

    @Test
    @Tag("AC-5.1")
    @DisplayName("D12, A5: the catalogue is sorted by code point (U+FF21 before an emoji), as C.UTF-8 orders")
    void theCatalogueIsInCodePointOrder() throws Exception {
        created(mockMvc, GUS, CHECKOUT, "features/order.feature",
                "@\uD83D\uDE00 @\uFF21 @b @B @\u00E9\nFeature: Order\n");

        assertThat(JsonPath.<List<String>>read(body(perform(mockMvc, DAN, get("/api/projects/" + CHECKOUT + "/tags"))),
                "$[*].name")).containsExactly("@B", "@b", "@\u00E9", "@\uFF21", "@\uD83D\uDE00");
    }

    @Test
    @Tag("AC-5.3")
    @DisplayName("BUG-6 fix: catalogue reuse keeps exact, case-sensitive names (U+200B, long emoji tags), one row each")
    void catalogueReuseKeepsExactNames() throws Exception {
        String longTag = "@" + "\uD83D\uDE00".repeat(999);
        created(mockMvc, GUS, CHECKOUT, "features/one.feature",
                "@Smoke @smoke @s\u200Bz @sz " + longTag + "\nFeature: One\n");
        created(mockMvc, GUS, CHECKOUT, "features/two.feature", "@smoke @SMOKE @sz " + longTag + "\nFeature: Two\n");

        assertThat(data.count("tags")).isEqualTo(6);
        String catalogue = body(perform(mockMvc, DAN, get("/api/projects/" + CHECKOUT + "/tags")));
        assertThat(JsonPath.<List<String>>read(catalogue, "$[*].name")).containsExactly("@SMOKE", "@Smoke", "@smoke",
                "@sz", "@s\u200Bz", longTag);
        assertThat(JsonPath.<List<Integer>>read(catalogue, "$[*].featureCount")).containsExactly(1, 1, 2, 2, 1, 2);
        assertThat(filtered("tag=@s%E2%80%8Bz")).containsExactly("features/one.feature");
        assertThat(filtered("tag=@Smoke")).containsExactly("features/one.feature");
    }

    private List<String> filtered(String query) throws Exception {
        MvcResult result = perform(mockMvc, DAN, get(URI.create(features(CHECKOUT) + "?" + query)));
        assertThat(result.getResponse().getStatus()).as(query + ": " + body(result)).isEqualTo(200);
        return JsonPath.read(body(result), "$[*].path");
    }

    private void assertValidationError(MvcResult result, String field) {
        assertProblem(result, 400, "VALIDATION_FAILED");
        assertThat(JsonPath.<List<String>>read(body(result), "$.errors[*].field")).contains(field);
    }
}
