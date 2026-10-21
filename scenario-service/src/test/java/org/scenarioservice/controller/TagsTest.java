package org.scenarioservice.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.scenarioservice.support.AccessCheckStub;
import org.scenarioservice.support.FeatureData;
import org.scenarioservice.support.GherkinSources;
import org.scenarioservice.support.ScenarioFixture.User;
import org.scenarioservice.support.ScenarioServiceIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.scenarioservice.controller.CreateFeatureTest.errorFields;
import static org.scenarioservice.support.FeatureCalls.created;
import static org.scenarioservice.support.FeatureCalls.features;
import static org.scenarioservice.support.FeatureCalls.fields;
import static org.scenarioservice.support.FeatureCalls.list;
import static org.scenarioservice.support.FeatureCalls.perform;
import static org.scenarioservice.support.FeatureCalls.save;
import static org.scenarioservice.support.FeatureCalls.view;
import static org.scenarioservice.support.ProblemAssertions.assertProblem;
import static org.scenarioservice.support.ProblemAssertions.body;
import static org.scenarioservice.support.ScenarioFixture.ANA;
import static org.scenarioservice.support.ScenarioFixture.BILLING;
import static org.scenarioservice.support.ScenarioFixture.CHECKOUT;
import static org.scenarioservice.support.ScenarioFixture.DAN;
import static org.scenarioservice.support.ScenarioFixture.EVE;
import static org.scenarioservice.support.ScenarioFixture.GUS;
import static org.scenarioservice.support.ScenarioFixture.HAL;
import static org.scenarioservice.support.ScenarioFixture.IVY;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * US-5 (T11, end to end): the tag catalogue and the one-tag filter of the feature list (D12, D19 #5). Background:
 * Gus created Payment and Address in Checkout, Ana created Invoice in Billing.
 */
@ScenarioServiceIntegrationTest
class TagsTest {

    private static final String PATH = "features/checkout/payment.feature";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FeatureData data;

    private UUID payment;

    @BeforeEach
    void background() throws Exception {
        data.reset();
        AccessCheckStub.get().reset();
        payment = created(mockMvc, GUS, CHECKOUT, PATH, GherkinSources.PAYMENT);
        created(mockMvc, GUS, CHECKOUT, "features/Address.feature", GherkinSources.ADDRESS);
        created(mockMvc, ANA, BILLING, "features/invoice.feature", GherkinSources.INVOICE);
    }

    @Test
    @Tag("AC-5.1")
    @DisplayName("AC-5.1 The tag catalogue lists every tag in use, with the number of features using it")
    void theCatalogueListsTagsInUse() throws Exception {
        MvcResult result = tags(DAN, CHECKOUT);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonPath.<List<Map<String, Object>>>read(body(result), "$")).containsExactly(
                Map.of("name", "@address", "featureCount", 1), Map.of("name", "@checkout", "featureCount", 1),
                Map.of("name", "@regression", "featureCount", 1), Map.of("name", "@smoke", "featureCount", 2));
    }

    @Test
    @Tag("AC-5.2")
    @DisplayName("AC-5.2 The catalogue follows edits")
    void theCatalogueFollowsEdits() throws Exception {
        String source = GherkinSources.PAYMENT.replace("      @regression\n", "")
                .replace("  @smoke\n  Scenario: Pay by card", "  @smoke @wip\n  Scenario: Pay by card");
        assertThat(source).doesNotContain("@regression").contains("@wip");
        save(mockMvc, GUS, CHECKOUT, payment, fields("path", PATH, "source", source, "baseVersion", 1));

        List<Map<String, Object>> catalogue = JsonPath.read(body(tags(DAN, CHECKOUT)), "$");

        assertThat(catalogue).extracting(tag -> tag.get("name")).doesNotContain("@regression");
        assertThat(catalogue).contains(Map.of("name", "@wip", "featureCount", 1));
    }

    @Test
    @Tag("AC-5.3")
    @DisplayName("AC-5.3 Tags are case-sensitive")
    void tagsAreCaseSensitive() throws Exception {
        created(mockMvc, GUS, CHECKOUT, "features/case.feature", "@Smoke\nFeature: Case\n");

        assertThat(JsonPath.<List<Map<String, Object>>>read(body(tags(DAN, CHECKOUT)), "$"))
                .contains(Map.of("name", "@Smoke", "featureCount", 1), Map.of("name", "@smoke", "featureCount", 2));
    }

    @ParameterizedTest(name = "AC-5.4 tag={0}")
    @CsvSource(delimiter = '|', value = {"@smoke|features/Address.feature,features/checkout/payment.feature",
            "@regression|features/checkout/payment.feature", "@billing|", "smoke|400", "@a%20b|400"})
    @Tag("AC-5.4")
    void theListCanBeFilteredByOneTag(String tag, String outcome) throws Exception {
        MvcResult result = perform(mockMvc, DAN, get(URI.create(features(CHECKOUT) + "?tag=" + tag)));

        if ("400".equals(outcome)) {
            assertThat(errorFields(assertProblem(result, 400, "VALIDATION_FAILED"))).contains("tag");
        } else {
            assertThat(JsonPath.<List<String>>read(body(result), "$[*].path"))
                    .containsExactlyElementsOf(outcome == null ? List.of() : List.of(outcome.split(",")));
        }
    }

    @ParameterizedTest(name = "AC-5.5 {0} reads tags: {1}")
    @CsvSource({"hal,200", "ivy,403", "eve,403"})
    @Tag("AC-5.5")
    void everyoneWithAccessCanReadTags(String actor, int status) throws Exception {
        User user = switch (actor) {
            case "hal" -> HAL;
            case "ivy" -> IVY;
            default -> EVE;
        };

        MvcResult result = tags(user, CHECKOUT);

        if (status == 200) {
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
        } else {
            assertProblem(result, 403, "FORBIDDEN");
        }
    }

    @Test
    @Tag("AC-5.6")
    @DisplayName("AC-5.6 Projects don't share tags")
    void projectsDontShareTags() throws Exception {
        assertThat(JsonPath.<List<Map<String, Object>>>read(body(tags(ANA, BILLING)), "$"))
                .containsExactly(Map.of("name", "@billing", "featureCount", 1));
    }

    @ParameterizedTest(name = "AC-5.7 {0}")
    @ValueSource(strings = {"tag=@", "tag=@smoke&tag=@checkout", "tag=@a%C2%A0b", "tag=@a%C2%85b", "long", "tag=",
            "tag=@a%EF%BB%BFb", "tag=@a%09b"})
    @Tag("AC-5.7")
    void aMalformedTagFilterIsAValidationError(String query) throws Exception {
        String value = query.equals("long") ? "tag=@" + "a".repeat(1000) : query;

        MvcResult result = perform(mockMvc, DAN, get(URI.create(features(CHECKOUT) + "?" + value)));

        assertThat(errorFields(assertProblem(result, 400, "VALIDATION_FAILED"))).containsExactly("tag");
    }

    @Test
    @Tag("AC-5.7")
    @DisplayName("D19 #5: a tag of exactly 1,000 code points is accepted")
    void aTagOfOneThousandCodePointsIsAccepted() throws Exception {
        MvcResult result = perform(mockMvc, DAN, get(URI.create(features(CHECKOUT) + "?tag=@" + "a".repeat(999))));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @Tag("AC-5.8")
    @DisplayName("AC-5.8 A tag written twice on one element is listed once")
    void aRepeatedTagIsListedOnce() throws Exception {
        UUID twice = created(mockMvc, GUS, CHECKOUT, "features/twice.feature",
                "@a @b @a\nFeature: Twice\n  Scenario: S\n    Given a step\n");

        assertThat(JsonPath.<List<String>>read(body(view(mockMvc, GUS, CHECKOUT, twice)), "$.tags"))
                .containsExactly("@a", "@b");
        assertThat(JsonPath.<List<String>>read(body(perform(mockMvc, GUS, get(features(CHECKOUT) + "/" + twice
                + "/test-cases"))), "$[0].tags")).containsExactly("@a", "@b");
        assertThat(JsonPath.<List<Map<String, Object>>>read(body(tags(DAN, CHECKOUT)), "$"))
                .contains(Map.of("name", "@a", "featureCount", 1));
    }

    @Test
    @DisplayName("D19 follow-up: a tag containing U+200B is stored and matched exactly")
    void aTagWithAZeroWidthSpaceIsMatchedExactly() throws Exception {
        created(mockMvc, GUS, CHECKOUT, "features/zw.feature", "@a\u200Bb\nFeature: Zero width\n");

        assertThat(JsonPath.<List<String>>read(body(perform(mockMvc, DAN,
                get(URI.create(features(CHECKOUT) + "?tag=@a%E2%80%8Bb")))), "$[*].path"))
                .containsExactly("features/zw.feature");
        assertThat(JsonPath.<List<Object>>read(body(perform(mockMvc, DAN,
                get(URI.create(features(CHECKOUT) + "?tag=@ab")))), "$")).isEmpty();
    }

    @Test
    @Tag("AC-2.2")
    @DisplayName("AC-2.2 The list's tags member is the Feature line's tags, in text order")
    void theListTagsMember() throws Exception {
        assertThat(JsonPath.<List<Object>>read(body(list(mockMvc, DAN, CHECKOUT)), "$[*].tags"))
                .containsExactly(List.of("@address", "@smoke"), List.of("@checkout"));
    }

    private MvcResult tags(User user, UUID projectId) throws Exception {
        return perform(mockMvc, user, get("/api/projects/" + projectId + "/tags"));
    }
}
