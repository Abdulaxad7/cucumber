package org.scenarioservice.configuration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.scenarioservice.support.AccessCheckStub;
import org.scenarioservice.support.FeatureData;
import org.scenarioservice.support.ScenarioServiceIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.scenarioservice.support.FeatureCalls.features;
import static org.scenarioservice.support.FeatureCalls.perform;
import static org.scenarioservice.support.ProblemAssertions.assertProblem;
import static org.scenarioservice.support.ScenarioFixture.CHECKOUT;
import static org.scenarioservice.support.ScenarioFixture.GUS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * AC-7.15, create rows (D8, D20 #4; end to end since T8): a JSON number or boolean is not a string; {@code null}
 * reaches validation. The {@code PUT} rows and {@code baseVersion} rules are in {@code controller/SaveFeatureTest}.
 */
@ScenarioServiceIntegrationTest
class StrictJsonTest {

    private static final String ADDRESS = "\"@address @smoke\\nFeature: Address\\n\"";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FeatureData data;

    @BeforeEach
    void reset() {
        data.reset();
        AccessCheckStub.get().reset();
    }

    @ParameterizedTest(name = "AC-7.15 {0} is malformed")
    @ValueSource(strings = {
            "{\"source\":" + ADDRESS + ",\"path\":1}",
            "{\"source\":" + ADDRESS + ",\"path\":true}",
            "{\"path\":\"features/address.feature\",\"source\":false}",
            "{\"path\":\"features/address.feature\",\"source\":" + ADDRESS + ",\"message\":1.5}",
            "{\"path\":[\"features/address.feature\"],\"source\":" + ADDRESS + "}",
            "{\"path\":{\"x\":1},\"source\":" + ADDRESS + "}"})
    @Tag("AC-7.15")
    @DisplayName("AC-7.15 A number or boolean is not a string")
    void aNumberOrBooleanIsNotAString(String json) throws Exception {
        assertProblem(send(json), 400, "MALFORMED_REQUEST");
        assertThat(data.count("features")).isZero();
    }

    @Test
    @Tag("AC-7.15")
    @DisplayName("AC-7.15 \"path\": null is a validation error with an entry for path")
    void aNullPathIsAValidationError() throws Exception {
        MvcResult result = send("{\"source\":" + ADDRESS + ",\"path\":null}");

        assertThat(errorFields(assertProblem(result, 400, "VALIDATION_FAILED"))).containsExactly("path");
    }

    @SuppressWarnings("unchecked")
    private static List<String> errorFields(Map<String, Object> problem) {
        return ((List<Map<String, Object>>) problem.get("errors")).stream().map(error -> (String) error.get("field"))
                .distinct().toList();
    }

    private MvcResult send(String json) throws Exception {
        return perform(mockMvc, GUS, post(features(CHECKOUT)).contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
