package com.example.authservice.controller;

import com.example.authservice.repository.UserRepository;
import com.example.authservice.support.AcmeFixture;
import com.example.authservice.support.AuthServiceIntegrationTest;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;

import static com.example.authservice.support.ApiCalls.bearer;
import static com.example.authservice.support.ApiCalls.tokenOf;
import static com.example.authservice.support.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * AC-6.10: an anonymous accept with no body, or with an empty JSON body, reports the same per-field errors as {}:
 * exactly one entry each for "fullName" and "password" (D13, Q2). Background: Ben has invited fay@example.com as
 * MEMBER and received token T.
 */
@AuthServiceIntegrationTest
class AcceptBodyErrorsTest {

    private static final String FAY = "fay@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AcmeFixture fixture;

    @Autowired
    private UserRepository userRepository;

    private String t;

    @BeforeEach
    void seedAcmeAndInviteFay() throws Exception {
        String orgId = fixture.seed().orgId().toString();
        MvcResult invited = mockMvc.perform(post("/api/orgs/" + orgId + "/invitations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenOf(mockMvc, "ben@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + FAY + "\",\"role\":\"MEMBER\"}"))
                .andReturn();
        assertThat(invited.getResponse().getStatus()).isEqualTo(201);
        t = JsonPath.read(invited.getResponse().getContentAsString(), "$.token");
    }

    @ParameterizedTest(name = "AC-6.10 {0}")
    @ValueSource(strings = {"no body", "an empty body with Content-Type: application/json", "body {}"})
    @Tag("AC-6.10")
    void aMissingAcceptBodyReportsEachMissingField(String body) throws Exception {
        // When
        MvcResult result = mockMvc.perform(accept(body)).andReturn();

        // Then
        DocumentContext problem = assertProblem(result, 400, "VALIDATION_FAILED", acceptPath());
        List<Map<String, String>> errors = problem.read("$.errors");
        assertThat(errors).extracting(error -> error.get("field")).containsExactlyInAnyOrder("fullName", "password");
        assertThat(errors).allSatisfy(error -> assertThat(error.get("message")).isNotBlank());
        assertThat(errors).as("the same entries as for {}").isEqualTo(errorsFor("body {}"));
        assertThat(userRepository.findByEmail(FAY)).isEmpty();
    }

    private List<Map<String, String>> errorsFor(String body) throws Exception {
        MvcResult result = mockMvc.perform(accept(body)).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.errors");
    }

    private MockHttpServletRequestBuilder accept(String body) {
        MockHttpServletRequestBuilder request = post(acceptPath());
        return switch (body) {
            case "no body" -> request;
            case "an empty body with Content-Type: application/json" ->
                    request.contentType(MediaType.APPLICATION_JSON).content("");
            case "body {}" -> request.contentType(MediaType.APPLICATION_JSON).content("{}");
            default -> throw new IllegalArgumentException(body);
        };
    }

    private String acceptPath() {
        return "/api/auth/invitations/" + t + "/accept";
    }
}
