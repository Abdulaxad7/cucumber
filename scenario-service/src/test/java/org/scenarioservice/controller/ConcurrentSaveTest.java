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
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.scenarioservice.support.FeatureCalls.created;
import static org.scenarioservice.support.FeatureCalls.fields;
import static org.scenarioservice.support.FeatureCalls.save;
import static org.scenarioservice.support.ProblemAssertions.body;
import static org.scenarioservice.support.ScenarioFixture.ANA;
import static org.scenarioservice.support.ScenarioFixture.CHECKOUT;
import static org.scenarioservice.support.ScenarioFixture.GUS;

/**
 * AC-3.11 (D9, D14), repeated 5 times: simultaneous saves on one version produce exactly one new version; the others
 * are VERSION_CONFLICT with currentVersion 2. A lock-timeout 409 CONFLICT here is a stop condition (D15).
 */
@ScenarioServiceIntegrationTest
class ConcurrentSaveTest {

    private static final String PATH = "features/checkout/payment.feature";

    private final AccessCheckStub stub = AccessCheckStub.get();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FeatureData data;

    private UUID payment;

    @BeforeEach
    void gusHasCreatedPayment() throws Exception {
        data.reset();
        stub.reset();
        payment = created(mockMvc, GUS, CHECKOUT, PATH, GherkinSources.PAYMENT);
    }

    @RepeatedTest(5)
    @Tag("AC-3.11")
    void simultaneousSavesProduceOneVersion() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(5);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MvcResult>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 5; i++) {
                User user = i % 2 == 0 ? ANA : GUS;
                String source = GherkinSources.PAYMENT.replace("Customers pay for their basket.",
                        "Customers pay for their basket, variant " + i + ".");
                results.add(pool.submit(() -> {
                    start.await();
                    return save(mockMvc, user, CHECKOUT, payment, fields("path", PATH, "source", source,
                            "baseVersion", 1));
                }));
            }
            start.countDown();
            int saved = 0;
            List<String> codes = new ArrayList<>();
            for (Future<MvcResult> result : results) {
                MvcResult done = result.get(30, TimeUnit.SECONDS);
                if (done.getResponse().getStatus() == 200) {
                    saved++;
                    assertThat(JsonPath.<Integer>read(body(done), "$.currentVersion")).isEqualTo(2);
                } else {
                    codes.add(JsonPath.read(body(done), "$.code"));
                    assertThat(JsonPath.<Integer>read(body(done), "$.currentVersion")).isEqualTo(2);
                }
            }

            assertThat(saved).isEqualTo(1);
            assertThat(codes).as("the four losers").hasSize(4).containsOnly("VERSION_CONFLICT");
            assertThat(data.count("feature_versions")).isEqualTo(2);
        } finally {
            pool.shutdownNow();
        }
    }
}
