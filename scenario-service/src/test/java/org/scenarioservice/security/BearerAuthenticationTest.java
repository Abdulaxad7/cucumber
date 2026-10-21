package org.scenarioservice.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.scenarioservice.support.AccessCheckStub;
import org.scenarioservice.support.ScenarioFixture;
import org.scenarioservice.support.ScenarioServiceIntegrationTest;
import org.scenarioservice.support.TestTokens;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.scenarioservice.support.ProblemAssertions.assertProblem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * AC-7.1 and AC-7.13 (D6, on the real list endpoint since T8): every request needs a valid bearer token, and the
 * scheme is matched in any letter case. "The access check stub received no request" is in
 * {@code controller/FeatureAccessTest}.
 */
@ScenarioServiceIntegrationTest
class BearerAuthenticationTest {

    private static final UUID GUS = ScenarioFixture.GUS.id();
    private static final String EMAIL = ScenarioFixture.GUS.email();
    private static final String PROBE = "/api/projects/" + ScenarioFixture.CHECKOUT + "/features";

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest(name = "AC-7.1 Authorization {0} is rejected")
    @ValueSource(strings = {"(none)", "Basic", "expired", "other secret", "HS256", "no exp", "no sub", "sub 1-1-1-1-1",
            "alg none", "Bearer without a token"})
    @Tag("AC-7.1")
    @DisplayName("AC-7.1 Requests without a valid token are rejected before any access check")
    void requestsWithoutAValidTokenAreRejected(String authorization) throws Exception {
        MockHttpServletRequestBuilder request = get(PROBE);
        String header = switch (authorization) {
            case "(none)" -> null;
            case "Basic" -> "Basic Z3VzOnNlY3JldA==";
            case "expired" -> "Bearer " + TestTokens.expired(GUS, EMAIL);
            case "other secret" -> "Bearer " + TestTokens.signedWithAnotherSecret(GUS, EMAIL);
            case "HS256" -> "Bearer " + TestTokens.signedWithHs256(GUS, EMAIL);
            case "no exp" -> "Bearer " + TestTokens.withoutExpiration(GUS, EMAIL);
            case "no sub" -> "Bearer " + TestTokens.withoutSubject(EMAIL);
            case "sub 1-1-1-1-1" -> "Bearer " + TestTokens.withSubject("1-1-1-1-1", EMAIL);
            case "alg none" -> "Bearer " + TestTokens.unsigned(GUS, EMAIL);
            default -> "Bearer ";
        };
        if (header != null) {
            request.header(HttpHeaders.AUTHORIZATION, header);
        }

        MvcResult result = mockMvc.perform(request).andReturn();

        assertProblem(result, 401, "UNAUTHENTICATED");
        assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
    }

    @ParameterizedTest(name = "AC-7.13 the scheme \"{0}\" is accepted")
    @ValueSource(strings = {"Bearer", "bearer", "BEARER"})
    @Tag("AC-7.13")
    @DisplayName("AC-7.13 The bearer scheme is matched in any letter case")
    void theBearerSchemeIsMatchedInAnyLetterCase(String scheme) throws Exception {
        AccessCheckStub.get().reset();
        String token = TestTokens.valid(GUS, EMAIL);

        MvcResult result = mockMvc.perform(get(PROBE).header(HttpHeaders.AUTHORIZATION, scheme + " " + token))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(AccessCheckStub.get().received()).singleElement()
                .satisfies(received -> assertThat(received.authorization()).isEqualTo("Bearer " + token));
    }
}
