package com.example.authservice.controller;

import com.example.authservice.enums.OrganizationRole;
import com.example.authservice.model.OrgMembership;
import com.example.authservice.repository.OrgMembershipRepository;
import com.example.authservice.support.AcmeFixture;
import com.example.authservice.support.AcmeFixture.Acme;
import com.example.authservice.support.AuthServiceIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static com.example.authservice.support.ApiCalls.bearer;
import static com.example.authservice.support.ApiCalls.tokenOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * E9: POST /api/orgs no longer fails with a NullPointerException. The response body is completed in T11 (AC-4.1).
 */
@AuthServiceIntegrationTest
class OrganizationCreationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AcmeFixture fixture;

    @Autowired
    private OrgMembershipRepository orgMembershipRepository;

    private Acme acme;

    @BeforeEach
    void seedAcme() {
        acme = fixture.seed();
    }

    @Test
    @DisplayName("Creating an organization succeeds and makes the creator its owner (body completed in T11)")
    void creatingAnOrganizationSucceeds() throws Exception {
        // When
        MvcResult result = mockMvc.perform(post("/api/orgs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenOf(mockMvc, "eve@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Globex QA\"}"))
                .andReturn();

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        List<OrgMembership> eveMemberships = orgMembershipRepository.findByUser(acme.eve());
        assertThat(eveMemberships).singleElement().satisfies(membership -> {
            assertThat(membership.getRole()).isEqualTo(OrganizationRole.OWNER);
            assertThat(membership.getOrganization().getName()).isEqualTo("Globex QA");
        });
    }
}
