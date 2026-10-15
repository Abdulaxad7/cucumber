package com.example.authservice.controller;

import com.example.authservice.enums.OrganizationRole;
import com.example.authservice.model.Organization;
import com.example.authservice.model.User;
import com.example.authservice.repository.OrganizationRepository;
import com.example.authservice.support.AcmeFixture;
import com.example.authservice.support.AuthServiceIntegrationTest;
import com.example.authservice.support.ProjectCalls;
import com.example.authservice.support.ProjectFixture;
import com.example.authservice.support.ProjectFixture.Projects;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * US-3 AC-3.6 (phase 4, T3, D3): lists ordered in SQL sort by code point, as on H2, because the database uses the
 * builtin {@code C.UTF-8} collation. A linguistic collation would put "beta QA" before "Zeta QA" and "ab" before
 * "a-z".
 */
@AuthServiceIntegrationTest
@Import(ProjectFixture.class)
class ListOrderTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectFixture fixture;

    @Autowired
    private AcmeFixture acmeFixture;

    @Autowired
    private OrganizationRepository organizationRepository;

    private Projects projects;
    private ProjectCalls calls;

    @BeforeEach
    void seedProjects() {
        projects = fixture.seed();
        calls = new ProjectCalls(mockMvc);
    }

    @Test
    @Tag("AC-3.6")
    @DisplayName("AC-3.6 Organizations are listed by name in code-point order")
    void organizationsAreListedByNameInCodePointOrder() throws Exception {
        // Given
        User fay = acmeFixture.user("fay@example.com", "Fay Lowe");
        Organization acme = organizationRepository.findById(projects.acme().orgId()).orElseThrow();
        acmeFixture.member(acmeFixture.organization("beta QA"), fay, OrganizationRole.MEMBER);
        acmeFixture.member(acme, fay, OrganizationRole.MEMBER);
        acmeFixture.member(acmeFixture.organization("Zeta QA"), fay, OrganizationRole.MEMBER);

        // When
        MvcResult result = calls.as("fay", get("/api/orgs"));

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonPath.<List<String>>read(result.getResponse().getContentAsString(), "$[*].name"))
                .containsExactly("Acme QA", "Zeta QA", "beta QA");
    }

    @Test
    @Tag("AC-3.6")
    @DisplayName("AC-3.6 Members are listed by email in code-point order")
    void membersAreListedByEmailInCodePointOrder() throws Exception {
        // Given
        Organization acme = organizationRepository.findById(projects.acme().orgId()).orElseThrow();
        acmeFixture.member(acme, acmeFixture.user("ab@example.com", "Ab Lee"), OrganizationRole.MEMBER);
        acmeFixture.member(acme, acmeFixture.user("a-z@example.com", "Az Lee"), OrganizationRole.MEMBER);

        // When
        MvcResult result = calls.as("ana", get("/api/orgs/" + projects.acme().orgId() + "/members"));

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        List<String> emails = JsonPath.read(result.getResponse().getContentAsString(), "$[*].email");
        assertThat(emails).containsSubsequence("a-z@example.com", "ab@example.com", "ana@example.com");
        assertThat(emails).isSortedAccordingTo(String::compareTo);
    }
}
