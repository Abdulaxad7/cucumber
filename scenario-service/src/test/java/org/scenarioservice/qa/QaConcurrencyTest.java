package org.scenarioservice.qa;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.scenarioservice.support.AccessCheckStub;
import org.scenarioservice.support.FeatureData;
import org.scenarioservice.support.GherkinSources;
import org.scenarioservice.support.ScenarioServiceIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.scenarioservice.support.FeatureCalls.create;
import static org.scenarioservice.support.FeatureCalls.created;
import static org.scenarioservice.support.FeatureCalls.features;
import static org.scenarioservice.support.FeatureCalls.fields;
import static org.scenarioservice.support.FeatureCalls.perform;
import static org.scenarioservice.support.FeatureCalls.save;
import static org.scenarioservice.support.FeatureCalls.view;
import static org.scenarioservice.support.ProblemAssertions.body;
import static org.scenarioservice.support.ScenarioFixture.ANA;
import static org.scenarioservice.support.ScenarioFixture.BILLING;
import static org.scenarioservice.support.ScenarioFixture.CHECKOUT;
import static org.scenarioservice.support.ScenarioFixture.GUS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * QA (adversarial, D9, D11, D14; AC-1.10, AC-3.11, AC-6.4, AC-6.8): races on create, save, archive and restore. Each
 * case runs 5 times. Outcomes may differ by interleaving, but every answer is one of the spec's, never a 5xx or the
 * safety net's 409 CONFLICT, and the stored state is consistent with the answers.
 */
@ScenarioServiceIntegrationTest
class QaConcurrencyTest {

    private static final String PATH = "features/checkout/payment.feature";

    private final AccessCheckStub stub = AccessCheckStub.get();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FeatureData data;

    @BeforeEach
    void reset() {
        data.reset();
        stub.reset();
    }

    @RepeatedTest(5)
    @Tag("AC-1.10")
    void simultaneousCreatesWithDifferentPathsAllSucceed() throws Exception {
        List<Callable<MvcResult>> calls = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String path = "features/f" + i + ".feature";
            String source = "@shared @new" + i + "\nFeature: F\n";
            org.scenarioservice.support.ScenarioFixture.User user = i % 2 == 0 ? ANA : GUS;
            calls.add(() -> create(mockMvc, user, CHECKOUT, path, source));
        }

        assertThat(statuses(together(calls))).containsOnly(201);
        assertThat(data.count("features")).isEqualTo(5);
        assertThat(catalogue(CHECKOUT)).containsEntry("@shared", 5);
    }

    @RepeatedTest(5)
    @Tag("AC-6.8")
    void archiveAndRestoreRace() throws Exception {
        UUID payment = created(mockMvc, GUS, CHECKOUT, PATH, GherkinSources.PAYMENT);
        List<Callable<MvcResult>> calls = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            String operation = i % 2 == 0 ? "/archive" : "/restore";
            org.scenarioservice.support.ScenarioFixture.User user = i % 3 == 0 ? ANA : GUS;
            calls.add(() -> perform(mockMvc, user, post(features(CHECKOUT) + "/" + payment + operation)));
        }

        List<MvcResult> results = together(calls);

        assertThat(statuses(results)).containsOnly(200);
        Map<String, Object> state = JsonPath.read(body(view(mockMvc, GUS, CHECKOUT, payment)), "$");
        assertThat(state).containsEntry("currentVersion", 1);
        assertThat(data.count("features")).isEqualTo(1);
    }

    @RepeatedTest(5)
    @Tag("AC-6.2")
    void saveAndArchiveRace() throws Exception {
        UUID payment = created(mockMvc, GUS, CHECKOUT, PATH, GherkinSources.PAYMENT);
        List<Callable<MvcResult>> calls = List.of(
                () -> save(mockMvc, GUS, CHECKOUT, payment, fields("path", PATH, "source", GherkinSources.PAYMENT_V2,
                        "baseVersion", 1)),
                () -> perform(mockMvc, ANA, post(features(CHECKOUT) + "/" + payment + "/archive")));

        List<MvcResult> results = together(calls);

        MvcResult saved = results.get(0);
        assertThat(results.get(1).getResponse().getStatus()).isEqualTo(200);
        if (saved.getResponse().getStatus() == 200) {
            assertThat(data.count("feature_versions")).isEqualTo(2);
        } else {
            assertThat(saved.getResponse().getStatus()).isEqualTo(409);
            assertThat(JsonPath.<String>read(body(saved), "$.code")).isEqualTo("FEATURE_ARCHIVED");
            assertThat(data.count("feature_versions")).isEqualTo(1);
        }
        assertThat(JsonPath.<String>read(body(view(mockMvc, GUS, CHECKOUT, payment)), "$.archivedAt")).isNotNull();
    }

    @RepeatedTest(5)
    @Tag("AC-6.4")
    void restoreAndCreateRaceForOnePath() throws Exception {
        UUID payment = created(mockMvc, GUS, CHECKOUT, PATH, GherkinSources.PAYMENT);
        perform(mockMvc, GUS, post(features(CHECKOUT) + "/" + payment + "/archive"));
        List<Callable<MvcResult>> calls = List.of(
                () -> perform(mockMvc, GUS, post(features(CHECKOUT) + "/" + payment + "/restore")),
                () -> create(mockMvc, ANA, CHECKOUT, "Features/Checkout/PAYMENT.feature", GherkinSources.ADDRESS));

        List<MvcResult> results = together(calls);

        List<Integer> statuses = statuses(results);
        assertThat(List.of(List.of(200, 409), List.of(409, 201)))
                .as("either the restore (200) or the create (201) wins").contains(statuses);
        MvcResult loser = statuses.get(0) == 409 ? results.get(0) : results.get(1);
        assertThat(JsonPath.<String>read(body(loser), "$.code")).isEqualTo("FEATURE_PATH_TAKEN");
        List<Object> active = JsonPath.read(body(perform(mockMvc, GUS, get(features(CHECKOUT)))), "$");
        assertThat(active).hasSize(1);
    }

    @RepeatedTest(5)
    @Tag("AC-5.2")
    void savesIntroducingOneNewTagInTwoFeatures() throws Exception {
        UUID first = created(mockMvc, GUS, CHECKOUT, "features/one.feature", "Feature: One\n");
        UUID second = created(mockMvc, GUS, CHECKOUT, "features/two.feature", "Feature: Two\n");
        UUID billing = created(mockMvc, ANA, BILLING, "features/three.feature", "Feature: Three\n");
        List<Callable<MvcResult>> calls = List.of(
                () -> save(mockMvc, GUS, CHECKOUT, first, fields("path", "features/one.feature", "source",
                        "@brand-new\nFeature: One\n", "baseVersion", 1)),
                () -> save(mockMvc, ANA, CHECKOUT, second, fields("path", "features/two.feature", "source",
                        "@brand-new\nFeature: Two\n", "baseVersion", 1)),
                () -> save(mockMvc, ANA, BILLING, billing, fields("path", "features/three.feature", "source",
                        "@brand-new\nFeature: Three\n", "baseVersion", 1)));

        assertThat(statuses(together(calls))).containsOnly(200);
        assertThat(catalogue(CHECKOUT)).containsEntry("@brand-new", 2);
        assertThat(catalogue(BILLING)).containsEntry("@brand-new", 1);
    }

    private Map<String, Integer> catalogue(UUID projectId) throws Exception {
        String tags = body(perform(mockMvc, ANA, get("/api/projects/" + projectId + "/tags")));
        List<String> names = JsonPath.read(tags, "$[*].name");
        List<Integer> counts = JsonPath.read(tags, "$[*].featureCount");
        Map<String, Integer> catalogue = new java.util.LinkedHashMap<>();
        for (int i = 0; i < names.size(); i++) {
            catalogue.put(names.get(i), counts.get(i));
        }
        return catalogue;
    }

    private static List<Integer> statuses(List<MvcResult> results) {
        List<Integer> statuses = new ArrayList<>();
        for (MvcResult result : results) {
            int status = result.getResponse().getStatus();
            assertThat(status).as(body(result)).isLessThan(500);
            if (status == 409) {
                assertThat(JsonPath.<String>read(body(result), "$.code")).as("no safety-net CONFLICT")
                        .isNotEqualTo("CONFLICT");
            }
            statuses.add(status);
        }
        return statuses;
    }

    private static List<MvcResult> together(List<Callable<MvcResult>> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (Callable<MvcResult> call : calls) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return call.call();
                }));
            }
            start.countDown();
            List<MvcResult> results = new ArrayList<>();
            for (Future<MvcResult> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
