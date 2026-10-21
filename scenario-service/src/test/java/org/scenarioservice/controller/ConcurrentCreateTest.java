package org.scenarioservice.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.scenarioservice.support.AccessCheckStub;
import org.scenarioservice.support.FeatureData;
import org.scenarioservice.support.GherkinSources;
import org.scenarioservice.support.ScenarioFixture.User;
import org.scenarioservice.support.ScenarioServiceIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.scenarioservice.support.FeatureCalls.create;
import static org.scenarioservice.support.ProblemAssertions.body;
import static org.scenarioservice.support.ScenarioFixture.ANA;
import static org.scenarioservice.support.ScenarioFixture.CHECKOUT;
import static org.scenarioservice.support.ScenarioFixture.GUS;

/**
 * AC-1.10 (D14 advisory lock, P17), repeated 5 times: simultaneous creations with one path create exactly one
 * feature. A lock-timeout 409 CONFLICT here is a stop condition (D15), so the losers must all be
 * FEATURE_PATH_TAKEN.
 */
@ScenarioServiceIntegrationTest
class ConcurrentCreateTest {

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
    void simultaneousCreationsWithOnePathCreateOneFeature() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(5);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MvcResult>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 5; i++) {
                User user = i % 2 == 0 ? ANA : GUS;
                results.add(pool.submit(() -> {
                    start.await();
                    return create(mockMvc, user, CHECKOUT, "features/address.feature", GherkinSources.ADDRESS);
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            List<String> codes = new ArrayList<>();
            for (Future<MvcResult> result : results) {
                MvcResult done = result.get(30, TimeUnit.SECONDS);
                statuses.add(done.getResponse().getStatus());
                if (done.getResponse().getStatus() != 201) {
                    codes.add(JsonPath.read(body(done), "$.code"));
                }
            }

            assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
            assertThat(codes).as("the four losers").hasSize(4).containsOnly("FEATURE_PATH_TAKEN");
            assertThat(data.count("features")).isEqualTo(1);
            assertThat(data.count("feature_versions")).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
