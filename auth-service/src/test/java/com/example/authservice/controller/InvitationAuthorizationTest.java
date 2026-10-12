package com.example.authservice.controller;

import com.example.authservice.support.AcmeFixture;
import com.example.authservice.support.AcmeFixture.Acme;
import com.example.authservice.support.AuthServiceIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static com.example.authservice.support.ApiCalls.bearer;
import static com.example.authservice.support.ApiCalls.tokenOf;
import static com.example.authservice.support.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * AC-5.2, authorization rows only: who may invite is decided by the caller's role in the database (D3). The
 * role-ceiling row (Ben invites an OWNER: 422 ROLE_CEILING) needs the invitation rules of T12.
 */
@AuthServiceIntegrationTest
class InvitationAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AcmeFixture fixture;

    private Acme acme;

    @BeforeEach
    void seedAcme() {
        acme = fixture.seed();
    }

    @ParameterizedTest(name = "AC-5.2 (authorization rows only) {0} invites with role {1}: {2}")
    @CsvSource({
            "ana, OWNER,  201",
            "ana, ADMIN,  201",
            "ben, ADMIN,  201",
            "ben, MEMBER, 201",
            "ben, READER, 201",
            "cai, READER, 403",
            "dan, READER, 403",
            "eve, READER, 403"
    })
    @Tag("AC-5.2")
    void whoMayInvite(String actor, String role, int status) throws Exception {
        // Given
        String path = "/api/orgs/" + acme.orgId() + "/invitations";

        // When
        MvcResult result = mockMvc.perform(post(path)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenOf(mockMvc, actor + "@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"fay@example.com\",\"role\":\"" + role + "\"}"))
                .andReturn();

        // Then
        if (status == 403) {
            assertProblem(result, 403, "FORBIDDEN", path);
        } else {
            assertThat(result.getResponse().getStatus()).isEqualTo(status);
        }
    }
}
