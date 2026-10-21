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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.scenarioservice.support.FeatureCalls.create;
import static org.scenarioservice.support.FeatureCalls.created;
import static org.scenarioservice.support.FeatureCalls.features;
import static org.scenarioservice.support.FeatureCalls.fields;
import static org.scenarioservice.support.FeatureCalls.perform;
import static org.scenarioservice.support.FeatureCalls.save;
import static org.scenarioservice.support.FeatureCalls.view;
import static org.scenarioservice.support.ProblemAssertions.assertProblem;
import static org.scenarioservice.support.ProblemAssertions.body;
import static org.scenarioservice.support.ScenarioFixture.ANA;
import static org.scenarioservice.support.ScenarioFixture.CHECKOUT;
import static org.scenarioservice.support.ScenarioFixture.DAN;
import static org.scenarioservice.support.ScenarioFixture.EVE;
import static org.scenarioservice.support.ScenarioFixture.GUS;
import static org.scenarioservice.support.ScenarioFixture.HAL;
import static org.scenarioservice.support.ScenarioFixture.LEGACY;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * US-6 (T12, end to end): archive and restore (D11); nothing is ever hard-deleted. Background: Gus created Payment
 * and Address in Checkout.
 */
@ScenarioServiceIntegrationTest
class ArchiveRestoreTest {

    private static final String PATH = "features/checkout/payment.feature";

    private final AccessCheckStub stub = AccessCheckStub.get();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FeatureData data;

    private UUID payment;

    @BeforeEach
    void background() throws Exception {
        data.reset();
        stub.reset();
        payment = created(mockMvc, GUS, CHECKOUT, PATH, GherkinSources.PAYMENT);
        created(mockMvc, GUS, CHECKOUT, "features/Address.feature", GherkinSources.ADDRESS);
    }

    @Test
    @Tag("AC-6.1")
    @DisplayName("AC-6.1 An archived feature leaves the default list but stays readable")
    void anArchivedFeatureStaysReadable() throws Exception {
        MvcResult result = operation(GUS, payment, "archive");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonPath.<String>read(body(result), "$.archivedAt")).isNotNull();
        assertThat(paths(false, null)).containsExactly("features/Address.feature");
        assertThat(paths(true, null)).containsExactly(PATH);
        for (String suffix : List.of("", "/versions", "/test-cases")) {
            assertThat(perform(mockMvc, DAN, get(features(CHECKOUT) + "/" + payment + suffix)).getResponse()
                    .getStatus()).as(suffix).isEqualTo(200);
        }
    }

    @Test
    @Tag("AC-6.2")
    @DisplayName("AC-6.2 An archived feature can't be changed")
    void anArchivedFeatureCantBeChanged() throws Exception {
        operation(GUS, payment, "archive");

        assertProblem(save(mockMvc, GUS, CHECKOUT, payment, fields("path", PATH, "source", GherkinSources.PAYMENT_V2,
                "baseVersion", 1)), 409, "FEATURE_ARCHIVED");
        assertThat(JsonPath.<Integer>read(body(view(mockMvc, GUS, CHECKOUT, payment)), "$.currentVersion"))
                .isEqualTo(1);
    }

    @Test
    @Tag("AC-6.2")
    @DisplayName("D16 (M09): saving an archived feature with a stale baseVersion is FEATURE_ARCHIVED, not a conflict")
    void featureArchivedComesBeforeVersionConflict() throws Exception {
        operation(GUS, payment, "archive");

        assertProblem(save(mockMvc, GUS, CHECKOUT, payment, fields("path", PATH, "source", GherkinSources.PAYMENT_V2,
                "baseVersion", 7)), 409, "FEATURE_ARCHIVED");
    }

    @Test
    @Tag("AC-6.3")
    @DisplayName("AC-6.3 Archived features don't count in tags")
    void archivedFeaturesDontCountInTags() throws Exception {
        operation(GUS, payment, "archive");

        assertThat(JsonPath.<List<Map<String, Object>>>read(body(perform(mockMvc, GUS,
                get("/api/projects/" + CHECKOUT + "/tags"))), "$")).containsExactly(
                Map.of("name", "@address", "featureCount", 1), Map.of("name", "@smoke", "featureCount", 1));
        assertThat(paths(false, "@checkout")).isEmpty();
    }

    @Test
    @Tag("AC-6.4")
    @DisplayName("AC-6.4 An archived feature's path is free, and restoring it then conflicts")
    void anArchivedPathIsFreeAndRestoringConflicts() throws Exception {
        operation(GUS, payment, "archive");

        assertThat(create(mockMvc, GUS, CHECKOUT, PATH, GherkinSources.ADDRESS).getResponse().getStatus())
                .isEqualTo(201);
        assertProblem(operation(GUS, payment, "restore"), 409, "FEATURE_PATH_TAKEN");
        assertThat(JsonPath.<String>read(body(view(mockMvc, GUS, CHECKOUT, payment)), "$.archivedAt")).isNotNull();
    }

    @Test
    @Tag("AC-6.5")
    @DisplayName("AC-6.5 Restoring brings the feature back with its ids")
    void restoringKeepsTheIds() throws Exception {
        List<String> before = ids(body(view(mockMvc, GUS, CHECKOUT, payment)));
        operation(GUS, payment, "archive");

        MvcResult result = operation(GUS, payment, "restore");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonPath.<Object>read(body(result), "$.archivedAt")).isNull();
        assertThat(paths(false, null)).contains(PATH);
        assertThat(ids(body(view(mockMvc, GUS, CHECKOUT, payment)))).isEqualTo(before);
    }

    @ParameterizedTest(name = "AC-6.6 {0} archives: {1}")
    @CsvSource({"ana,200", "gus,200", "hal,403", "dan,403", "eve,403"})
    @Tag("AC-6.6")
    void onlyMembersAndAdminsCanArchive(String actor, int status) throws Exception {
        User user = switch (actor) {
            case "ana" -> ANA;
            case "gus" -> GUS;
            case "hal" -> HAL;
            case "dan" -> DAN;
            default -> EVE;
        };

        MvcResult result = operation(user, payment, "archive");

        if (status == 200) {
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
        } else {
            assertProblem(result, 403, "FORBIDDEN");
            assertThat(JsonPath.<Object>read(body(view(mockMvc, GUS, CHECKOUT, payment)), "$.archivedAt")).isNull();
        }
    }

    @ParameterizedTest(name = "AC-6.7 {0} an unknown feature")
    @ValueSource(strings = {"archive", "restore"})
    @Tag("AC-6.7")
    void anUnknownFeature(String operation) throws Exception {
        assertProblem(operation(GUS, UUID.randomUUID(), operation), 404, "FEATURE_NOT_FOUND");
    }

    @Test
    @Tag("AC-6.8")
    @DisplayName("AC-6.8 Archiving an archived feature leaves archivedAt unchanged")
    void archiveIsIdempotent() throws Exception {
        String first = JsonPath.read(body(operation(GUS, payment, "archive")), "$.archivedAt");

        MvcResult again = operation(GUS, payment, "archive");

        assertThat(again.getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonPath.<String>read(body(again), "$.archivedAt")).isEqualTo(first);
    }

    @Test
    @Tag("AC-6.8")
    @DisplayName("AC-6.8 Restoring an active feature leaves it active and updatedAt unchanged")
    void restoreIsIdempotent() throws Exception {
        String updatedAt = JsonPath.read(body(view(mockMvc, GUS, CHECKOUT, payment)), "$.updatedAt");

        MvcResult result = operation(GUS, payment, "restore");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonPath.<Object>read(body(result), "$.archivedAt")).isNull();
        assertThat(JsonPath.<String>read(body(result), "$.updatedAt")).isEqualTo(updatedAt);
    }

    @ParameterizedTest(name = "AC-6.9 a real {0} records who and when")
    @ValueSource(strings = {"archive", "restore"})
    @Tag("AC-6.9")
    void aRealArchiveOrRestoreRecordsWhoAndWhen(String operation) throws Exception {
        if (operation.equals("restore")) {
            operation(GUS, payment, "archive");
        }
        String before = body(view(mockMvc, GUS, CHECKOUT, payment));

        MvcResult result = operation(ANA, payment, operation);
        MvcResult again = operation(GUS, payment, operation);

        String after = body(result);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonPath.<String>read(after, "$.updatedBy")).isEqualTo(ANA.id().toString());
        assertThat(Instant.parse(JsonPath.read(after, "$.updatedAt")))
                .isAfter(Instant.parse(JsonPath.read(before, "$.updatedAt")));
        assertThat(JsonPath.<String>read(body(again), "$.updatedBy")).isEqualTo(ANA.id().toString());
        assertThat(JsonPath.<String>read(body(again), "$.updatedAt")).isEqualTo(JsonPath.read(after, "$.updatedAt"));
    }

    @Test
    @Tag("AC-6.10")
    @DisplayName("AC-6.10 The tag filter works on archived features too")
    void theTagFilterWorksOnArchivedFeatures() throws Exception {
        operation(GUS, payment, "archive");

        assertThat(paths(true, "@checkout")).containsExactly(PATH);
        assertThat(paths(true, "@address")).isEmpty();
    }

    @Test
    @Tag("AC-7.7")
    @DisplayName("AC-7.7 Archiving a feature of an archived project is 409 PROJECT_ARCHIVED")
    void archivingInAnArchivedProject() throws Exception {
        stub.answer(200, AccessCheckStub.accessBody(LEGACY, "MEMBER").replace("\"archived\":true",
                "\"archived\":false"));
        UUID legacy = created(mockMvc, GUS, LEGACY, "features/address.feature", GherkinSources.ADDRESS);
        stub.reset();

        assertProblem(perform(mockMvc, GUS, post(features(LEGACY) + "/" + legacy + "/archive")), 409,
                "PROJECT_ARCHIVED");
        assertThat(JsonPath.<Object>read(body(view(mockMvc, GUS, LEGACY, legacy)), "$.archivedAt")).isNull();
    }

    @Test
    @DisplayName("D11: archive and restore never delete a row")
    void nothingIsDeleted() throws Exception {
        long scenarios = data.count("scenarios");

        operation(GUS, payment, "archive");
        operation(GUS, payment, "restore");

        assertThat(data.count("features")).isEqualTo(2);
        assertThat(data.count("feature_versions")).isEqualTo(2);
        assertThat(data.count("scenarios")).isEqualTo(scenarios);
    }

    private MvcResult operation(User user, UUID featureId, String operation) throws Exception {
        return perform(mockMvc, user, post(features(CHECKOUT) + "/" + featureId + "/" + operation));
    }

    private List<String> paths(boolean archived, String tag) throws Exception {
        var request = get(features(CHECKOUT)).queryParam("archived", String.valueOf(archived));
        if (tag != null) {
            request.queryParam("tag", tag);
        }
        return JsonPath.read(body(perform(mockMvc, DAN, request)), "$[*].path");
    }

    private static List<String> ids(String feature) {
        return JsonPath.read(feature, "$..id");
    }
}
