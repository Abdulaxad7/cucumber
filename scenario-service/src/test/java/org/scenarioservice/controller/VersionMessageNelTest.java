package org.scenarioservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.scenarioservice.support.AccessCheckStub;
import org.scenarioservice.support.FeatureData;
import org.scenarioservice.support.GherkinSources;
import org.scenarioservice.support.ScenarioServiceIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.scenarioservice.controller.CreateFeatureTest.errorFields;
import static org.scenarioservice.support.FeatureCalls.create;
import static org.scenarioservice.support.FeatureCalls.fields;
import static org.scenarioservice.support.ProblemAssertions.assertProblem;
import static org.scenarioservice.support.ScenarioFixture.CHECKOUT;
import static org.scenarioservice.support.ScenarioFixture.GUS;

/**
 * D19 #9 as pm decided at review point A (contract 1.0.3): NEL (U+0085) is a control character, not Unicode-blank, so
 * trimming doesn't remove it and a message with a NEL is 400, even at its end.
 */
@ScenarioServiceIntegrationTest
class VersionMessageNelTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FeatureData data;

    @BeforeEach
    void reset() {
        data.reset();
        AccessCheckStub.get().reset();
    }

    @ParameterizedTest(name = "AC-3.14 (1.0.3) the message \"{0}\" (escaped) is 400")
    @ValueSource(strings = {"First draft\u0085", "\u0085First draft", "First\u0085draft"})
    @Tag("AC-3.14")
    @DisplayName("1.0.3: a NEL in the message is a validation error")
    void aNelInTheMessageIsAValidationError(String message) throws Exception {
        assertThat(errorFields(assertProblem(create(mockMvc, GUS, CHECKOUT, fields("path", "features/a.feature",
                "source", GherkinSources.ADDRESS, "message", message)), 400, "VALIDATION_FAILED")))
                .contains("message");
        assertThat(data.count("features")).isZero();
    }
}
