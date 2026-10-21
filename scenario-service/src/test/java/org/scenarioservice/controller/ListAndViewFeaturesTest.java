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
import static org.scenarioservice.support.FeatureCalls.create;
import static org.scenarioservice.support.FeatureCalls.created;
import static org.scenarioservice.support.FeatureCalls.features;
import static org.scenarioservice.support.FeatureCalls.list;
import static org.scenarioservice.support.FeatureCalls.perform;
import static org.scenarioservice.support.FeatureCalls.view;
import static org.scenarioservice.support.ProblemAssertions.assertProblem;
import static org.scenarioservice.support.ProblemAssertions.body;
import static org.scenarioservice.support.ScenarioFixture.ANA;
import static org.scenarioservice.support.ScenarioFixture.BILLING;
import static org.scenarioservice.support.ScenarioFixture.CAI;
import static org.scenarioservice.support.ScenarioFixture.CHECKOUT;
import static org.scenarioservice.support.ScenarioFixture.DAN;
import static org.scenarioservice.support.ScenarioFixture.EVE;
import static org.scenarioservice.support.ScenarioFixture.GUS;
import static org.scenarioservice.support.ScenarioFixture.HAL;
import static org.scenarioservice.support.ScenarioFixture.IVY;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * US-2 (T8, end to end), with AC-7.12's and AC-7.14's {@code archived} and id rows on the real endpoints: list and
 * view features. Background: Gus created Payment and Address in Checkout, Ana created Invoice in Billing.
 */
@ScenarioServiceIntegrationTest
class ListAndViewFeaturesTest {

    private final AccessCheckStub stub = AccessCheckStub.get();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FeatureData data;

    private UUID payment;
    private UUID invoice;

    @BeforeEach
    void background() throws Exception {
        data.reset();
        stub.reset();
        payment = created(mockMvc, GUS, CHECKOUT, "features/checkout/payment.feature", GherkinSources.PAYMENT);
        created(mockMvc, GUS, CHECKOUT, "features/Address.feature", GherkinSources.ADDRESS);
        invoice = created(mockMvc, ANA, BILLING, "features/invoice.feature", GherkinSources.INVOICE);
    }

    @ParameterizedTest(name = "AC-2.1 {0} lists: {1}")
    @CsvSource({"ana,200", "gus,200", "hal,200", "dan,200", "ivy,403", "eve,403"})
    @Tag("AC-2.1")
    void everyoneWithAccessCanList(String actor, int status) throws Exception {
        MvcResult result = list(mockMvc, user(actor), CHECKOUT);

        if (status == 200) {
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            assertThat(JsonPath.<List<Object>>read(body(result), "$")).hasSize(2);
        } else {
            assertProblem(result, 403, "FORBIDDEN");
        }
    }

    @Test
    @Tag("AC-2.2")
    @DisplayName("AC-2.2 The list shows each feature's summary, ordered by path ignoring case")
    void theListShowsSummaries() throws Exception {
        String body = body(list(mockMvc, DAN, CHECKOUT));

        assertThat(JsonPath.<List<String>>read(body, "$[*].path"))
                .containsExactly("features/Address.feature", "features/checkout/payment.feature");
        Map<String, Object> paymentEntry = JsonPath.read(body, "$[1]");
        assertThat(paymentEntry).containsEntry("id", payment.toString()).containsEntry("name", "Payment")
                .containsEntry("currentVersion", 1).containsEntry("tags", List.of("@checkout"))
                .containsEntry("testCaseCount", 4).containsEntry("archivedAt", null).containsKey("updatedAt")
                .containsKeys("projectId", "language", "createdBy", "createdAt", "updatedBy");
        Map<String, Object> addressEntry = JsonPath.read(body, "$[0]");
        assertThat(addressEntry).containsEntry("tags", List.of("@address", "@smoke")).containsEntry("testCaseCount", 1);
        assertThat(JsonPath.<List<Map<String, Object>>>read(body, "$")).allSatisfy(entry ->
                assertThat(entry).doesNotContainKeys("source", "children", "description"));
    }

    @ParameterizedTest(name = "AC-2.3 {0} views Payment: {1}")
    @CsvSource({"cai,200", "hal,200", "dan,200", "ivy,403", "eve,403"})
    @Tag("AC-2.3")
    void everyoneWithAccessCanView(String actor, int status) throws Exception {
        MvcResult result = view(mockMvc, user(actor), CHECKOUT, payment);

        if (status == 200) {
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            assertThat(JsonPath.<String>read(body(result), "$.source")).isEqualTo(GherkinSources.PAYMENT);
        } else {
            assertProblem(result, 403, "FORBIDDEN");
        }
    }

    @ParameterizedTest(name = "AC-2.4 feature id {0}")
    @ValueSource(strings = {"unknown", "invoice", "not-a-uuid"})
    @Tag("AC-2.4")
    void anUnknownOrForeignFeatureIsNotFound(String row) throws Exception {
        String id = switch (row) {
            case "unknown" -> UUID.randomUUID().toString();
            case "invoice" -> invoice.toString();
            default -> "not-a-uuid";
        };

        MvcResult result = perform(mockMvc, ANA, get(features(CHECKOUT) + "/" + id));

        if (row.equals("not-a-uuid")) {
            assertProblem(result, 400, "MALFORMED_REQUEST");
        } else {
            assertProblem(result, 404, "FEATURE_NOT_FOUND");
        }
    }

    @Test
    @Tag("AC-2.2")
    @DisplayName("A5 (M08): the list ignores case when ordering, so features/a.feature comes before features/B.feature")
    void theListOrderIgnoresCase() throws Exception {
        created(mockMvc, ANA, BILLING, "features/B.feature", GherkinSources.ADDRESS);
        created(mockMvc, ANA, BILLING, "features/a.feature", GherkinSources.ADDRESS);

        assertThat(JsonPath.<List<String>>read(body(list(mockMvc, ANA, BILLING)), "$[*].path"))
                .containsExactly("features/a.feature", "features/B.feature", "features/invoice.feature");
    }

    @Test
    @Tag("AC-2.5")
    @DisplayName("AC-2.5 Projects don't share features")
    void projectsDontShareFeatures() throws Exception {
        assertThat(JsonPath.<List<String>>read(body(list(mockMvc, ANA, BILLING)), "$[*].path"))
                .containsExactly("features/invoice.feature");
    }

    @Test
    @Tag("AC-2.6")
    @DisplayName("AC-2.6 The stored text comes back byte for byte")
    void theStoredTextComesBackByteForByte() throws Exception {
        String source = "Feature: Spaces \r\n  Scenario: S\r\n    Given a step\r\n    Then done";
        UUID id = created(mockMvc, GUS, CHECKOUT, "features/crlf.feature", source);

        assertThat(JsonPath.<String>read(body(view(mockMvc, GUS, CHECKOUT, id)), "$.source")).isEqualTo(source);
    }

    @ParameterizedTest(name = "AC-7.12 archived={0} is malformed")
    @ValueSource(strings = {"yes", "1", "True", "", "maybe"})
    @Tag("AC-7.12")
    void archivedHasOneSpelling(String value) throws Exception {
        assertProblem(perform(mockMvc, GUS, get(features(CHECKOUT)).queryParam("archived", value)), 400,
                "MALFORMED_REQUEST");
    }

    @ParameterizedTest(name = "AC-7.14 {0} is malformed, with no access check")
    @ValueSource(strings = {"/api/projects/%20{checkout}/features", "/api/projects/{checkout}%20/features",
            "/api/projects/%09{checkout}/features", "/api/projects/{checkout}/features/%20{payment}",
            "/api/projects/{checkout}/features?archived=%20true",
            "/api/projects/{checkout}/features?archived=false%20"})
    @Tag("AC-7.14")
    void idsAndArchivedAreMatchedExactly(String template) throws Exception {
        stub.reset();
        String url = template.replace("{checkout}", CHECKOUT.toString()).replace("{payment}", payment.toString());

        assertProblem(perform(mockMvc, GUS, get(URI.create(url))), 400, "MALFORMED_REQUEST");
        assertThat(stub.received()).isEmpty();
    }

    @Test
    @DisplayName("D11, D19 #6: archived=false lists active features and archived=true lists none yet")
    void theArchivedSwitchPicksTheSet() throws Exception {
        assertThat(JsonPath.<List<Object>>read(body(perform(mockMvc, GUS, get(features(CHECKOUT))
                .queryParam("archived", "false"))), "$")).hasSize(2);
        assertThat(JsonPath.<List<Object>>read(body(perform(mockMvc, GUS, get(features(CHECKOUT))
                .queryParam("archived", "true"))), "$")).isEmpty();
    }

    @Test
    @DisplayName("C6: the list and the view show the same ids as creation")
    void creatingTwiceGivesDistinctFeatures() throws Exception {
        MvcResult again = create(mockMvc, GUS, CHECKOUT, "features/other.feature", GherkinSources.ADDRESS);

        assertThat(JsonPath.<String>read(body(again), "$.id")).isNotEqualTo(payment.toString());
        assertThat(JsonPath.<List<String>>read(body(list(mockMvc, GUS, CHECKOUT)), "$[*].id"))
                .contains(payment.toString()).hasSize(3);
    }

    private static User user(String name) {
        return switch (name) {
            case "ana" -> ANA;
            case "cai" -> CAI;
            case "gus" -> GUS;
            case "hal" -> HAL;
            case "dan" -> DAN;
            case "ivy" -> IVY;
            default -> EVE;
        };
    }
}
