package org.scenarioservice.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.scenarioservice.support.AccessCheckStub;
import org.scenarioservice.support.FeatureData;
import org.scenarioservice.support.ScenarioServiceIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.scenarioservice.support.FeatureCalls.create;
import static org.scenarioservice.support.FeatureCalls.fields;
import static org.scenarioservice.support.FeatureCalls.save;
import static org.scenarioservice.support.ProblemAssertions.body;
import static org.scenarioservice.support.ScenarioFixture.CHECKOUT;
import static org.scenarioservice.support.ScenarioFixture.GUS;

/**
 * D9 id matching on structured keys (D21 #3, BUG-3, AC-3.15), and the mutants qa found survivable by backend-swe's
 * tests (M11, M12, M13, M21): kind, Rule name, header and each old row used once all take part in matching.
 */
@ScenarioServiceIntegrationTest
class IdMatchingTest {

    private static final String PATH = "features/keys.feature";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FeatureData data;

    @BeforeEach
    void reset() {
        data.reset();
        AccessCheckStub.get().reset();
    }

    @Test
    @Tag("AC-3.15")
    @DisplayName("AC-3.15 names that contain the old separator don't collide: both scenarios keep their own ids")
    void namesWithSeparatorsDontCollide() throws Exception {
        String v1 = "Feature: K\n  Rule: a\n    Scenario: b|1|SCENARIO|c\n      Given one\n"
                + "  Rule: a|1|SCENARIO|b\n    Scenario: c\n      Given two\n";
        String before = created(v1);

        MvcResult saved = saveAs(before, v1.replace("Given one", "Given one changed"));

        assertThat(saved.getResponse().getStatus()).isEqualTo(200);
        List<String> beforeIds = JsonPath.read(before, "$.children[*].children[*].id");
        assertThat(beforeIds).doesNotHaveDuplicates().hasSize(2);
        assertThat(JsonPath.<List<String>>read(body(saved), "$.children[*].children[*].id")).isEqualTo(beforeIds);
    }

    @Test
    @Tag("AC-3.15")
    @DisplayName("AC-3.15 headers are compared as lists: | a, b | c | and | a | b, c | differ; the row gets a new id")
    void headersAreComparedAsLists() throws Exception {
        String v1 = "Feature: K\n  Scenario Outline: O\n    Given <c>\n    Examples:\n      | a, b | c |\n"
                + "      | 1 | 2 |\n";
        String before = created(v1);

        MvcResult saved = saveAs(before, v1.replace("| a, b | c |", "| a | b, c |"));

        assertThat(saved.getResponse().getStatus()).isEqualTo(200);
        assertThat(rowIds(body(saved))).singleElement().isNotIn(rowIds(before));
    }

    @Test
    @DisplayName("M11: two identical new rows against one old row: only the first keeps the id")
    void anOldRowIsMatchedOnce() throws Exception {
        String v1 = "Feature: K\n  Scenario Outline: O\n    Given <x>\n    Examples:\n      | x |\n      | 1 |\n";
        String before = created(v1);
        String oldRow = rowIds(before).get(0);

        MvcResult saved = saveAs(before, v1 + "      | 1 |\n");

        assertThat(saved.getResponse().getStatus()).isEqualTo(200);
        List<String> after = rowIds(body(saved));
        assertThat(after).hasSize(2);
        assertThat(after.get(0)).isEqualTo(oldRow);
        assertThat(after.get(1)).isNotEqualTo(oldRow);
    }

    @Test
    @DisplayName("M12: a Scenario turned into an Outline with the same name gets a new id")
    void theKindTakesPartInMatching() throws Exception {
        String before = created("Feature: K\n  Scenario: S\n    Given x\n");

        MvcResult saved = saveAs(before, "Feature: K\n  Scenario Outline: S\n    Given <x>\n    Examples:\n"
                + "      | x |\n      | 1 |\n");

        assertThat(JsonPath.<String>read(body(saved), "$.children[0].id"))
                .isNotEqualTo(JsonPath.read(before, "$.children[0].id"));
    }

    @Test
    @DisplayName("M13: the same scenario name in two Rules keeps each Rule's id, even when the Rules swap places")
    void theRuleNameTakesPartInMatching() throws Exception {
        String ruleA = "  Rule: A\n    Scenario: S\n      Given in a\n";
        String ruleB = "  Rule: B\n    Scenario: S\n      Given in b\n";
        String before = created("Feature: K\n" + ruleA + ruleB);
        String idInA = JsonPath.read(before, "$.children[0].children[0].id");
        String idInB = JsonPath.read(before, "$.children[1].children[0].id");
        assertThat(idInA).isNotEqualTo(idInB);

        MvcResult saved = saveAs(before, "Feature: K\n" + ruleB + ruleA);

        assertThat(JsonPath.<String>read(body(saved), "$.children[0].name")).isEqualTo("B");
        assertThat(JsonPath.<String>read(body(saved), "$.children[0].children[0].id")).isEqualTo(idInB);
        assertThat(JsonPath.<String>read(body(saved), "$.children[1].children[0].id")).isEqualTo(idInA);
    }

    @Test
    @DisplayName("M21: rows aren't pooled across Examples blocks with different headers")
    void rowsArentPooledAcrossHeaders() throws Exception {
        String v1 = "Feature: K\n  Scenario Outline: O\n    Given <a>\n    Examples:\n      | a |\n      | 1 |\n";
        String before = created(v1);

        MvcResult saved = saveAs(before, v1.replace("| a |", "| b |").replace("<a>", "<b>"));

        assertThat(rowIds(body(saved))).singleElement().isNotIn(rowIds(before));
    }

    private String created(String source) throws Exception {
        MvcResult result = create(mockMvc, GUS, CHECKOUT, PATH, source);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return body(result);
    }

    private MvcResult saveAs(String created, String source) throws Exception {
        UUID id = UUID.fromString(JsonPath.read(created, "$.id"));
        return save(mockMvc, GUS, CHECKOUT, id, fields("path", PATH, "source", source, "baseVersion", 1));
    }

    private static List<String> rowIds(String feature) {
        return JsonPath.read(feature, "$..rows[*].id");
    }
}
