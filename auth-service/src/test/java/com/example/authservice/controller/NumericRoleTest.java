package com.example.authservice.controller;

import com.example.authservice.enums.OrganizationRole;
import com.example.authservice.model.OrgMembership;
import com.example.authservice.repository.InvitationRepository;
import com.example.authservice.repository.OrgMembershipRepository;
import com.example.authservice.support.AcmeFixture;
import com.example.authservice.support.AcmeFixture.Acme;
import com.example.authservice.support.AuthServiceIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static com.example.authservice.support.ApiCalls.bearer;
import static com.example.authservice.support.ApiCalls.tokenOf;
import static com.example.authservice.support.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * AC-5.11 and AC-7.8: a role sent as a number, or as a string of digits, is 400 MALFORMED_REQUEST and is never
 * mapped by the enum's declaration order (D3, D13).
 */
@AuthServiceIntegrationTest
class NumericRoleTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AcmeFixture fixture;

    @Autowired
    private InvitationRepository invitationRepository;

    @Autowired
    private OrgMembershipRepository orgMembershipRepository;

    private Acme acme;

    @BeforeEach
    void seedAcme() {
        acme = fixture.seed();
    }

    @ParameterizedTest(name = "AC-5.11 inviting with role {0}")
    @ValueSource(strings = {"0", "1", "3", "\"3\""})
    @Tag("AC-5.11")
    void aNumericRoleIsRejectedWhenInviting(String role) throws Exception {
        // Given
        String path = "/api/orgs/" + acme.orgId() + "/invitations";

        // When
        MvcResult result = mockMvc.perform(post(path)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenOf(mockMvc, "ana@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"fay@example.com\",\"role\":" + role + "}"))
                .andReturn();

        // Then
        assertProblem(result, 400, "MALFORMED_REQUEST", path);
        assertThat(invitationRepository.findAll()).as("Acme's invitations").isEmpty();
    }

    @ParameterizedTest(name = "AC-7.8 changing the role of Dan to {0}")
    @ValueSource(strings = {"0", "1", "\"2\""})
    @Tag("AC-7.8")
    void aNumericRoleIsRejectedWhenChangingARole(String role) throws Exception {
        // Given
        String path = "/api/orgs/" + acme.orgId() + "/members/" + acme.dan().getId();

        // When
        MvcResult result = mockMvc.perform(patch(path)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenOf(mockMvc, "ana@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":" + role + "}"))
                .andReturn();

        // Then
        assertProblem(result, 400, "MALFORMED_REQUEST", path);
        assertThat(orgMembershipRepository.findByUserIdAndOrganizationId(acme.dan().getId(), acme.orgId()))
                .get().extracting(OrgMembership::getRole).isEqualTo(OrganizationRole.READER);
    }
}
