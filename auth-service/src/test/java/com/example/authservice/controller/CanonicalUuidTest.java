package com.example.authservice.controller;

import com.example.authservice.repository.InvitationRepository;
import com.example.authservice.repository.OrgMembershipRepository;
import com.example.authservice.repository.ProjectMembershipRepository;
import com.example.authservice.repository.ProjectRepository;
import com.example.authservice.support.AuthServiceIntegrationTest;
import com.example.authservice.support.ProjectCalls;
import com.example.authservice.support.ProjectFixture;
import com.example.authservice.support.ProjectFixture.Projects;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.util.List;
import java.util.Locale;

import static com.example.authservice.support.ProblemAssertions.assertProblem;
import static com.example.authservice.support.ProjectCalls.project;
import static com.example.authservice.support.ProjectCalls.projects;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * US-7 AC-7.1 and AC-7.2 (phase 4, T13, D13, FU-10): a path id that isn't a canonical UUID (8-4-4-4-12 hexadecimal
 * digits) is 400 MALFORMED_REQUEST before authorization, so each resource has exactly one id string; upper-case digits
 * stay accepted. Background: the Projects fixture. Rows 2 and 3 are {unknown id} with one zero removed.
 */
@AuthServiceIntegrationTest
@Import(ProjectFixture.class)
class CanonicalUuidTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectFixture fixture;

    @Autowired
    private OrgMembershipRepository orgMembershipRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMembershipRepository projectMembershipRepository;

    @Autowired
    private InvitationRepository invitationRepository;

    private Projects projects;
    private ProjectCalls calls;

    @BeforeEach
    void seedProjects() {
        projects = fixture.seed();
        calls = new ProjectCalls(mockMvc);
    }

    @ParameterizedTest(name = "AC-7.1 {0} {1} {2}")
    @CsvSource(delimiter = '|', value = {
            "hal | GET    | /internal/projects/1-1-1-1-1/access",
            "ana | GET    | /api/orgs/3b9d5c1e-000-4000-8000-0000000000ff",
            "ana | GET    | /api/orgs/3b9d5c1e-0000-4000-8000-0000000000f",
            "ana | GET    | {acme-projects}/ab3b9d5c1e-0-0-0-0",
            "ana | DELETE | /api/orgs/{acme}/members/1-1-1-1-1",
            "ana | DELETE | /api/orgs/{acme}/invitations/1-1-1-1-1"})
    @Tag("AC-7.1")
    @DisplayName("AC-7.1 A UUID that isn't canonical is malformed")
    void aUuidThatIsntCanonicalIsMalformed(String actor, String method, String template) throws Exception {
        // Given
        String path = template.replace("{acme-projects}", projects(projects.acme().orgId()))
                .replace("{acme}", projects.acme().orgId().toString());
        List<Long> before = counts();

        // When
        MvcResult result = calls.as(actor, method.equals("GET") ? get(path) : delete(path));

        // Then
        assertProblem(result, 400, "MALFORMED_REQUEST", path);
        assertThat(counts()).as("nothing was stored").isEqualTo(before);
    }

    @ParameterizedTest(name = "AC-7.1 (BUG-2, W2) {0} {1}")
    @CsvSource(delimiter = '|', value = {
            "ana | /api/orgs/%20{acme}",
            "ana | /api/orgs/{acme}%20",
            "ana | /api/orgs/%09{acme}",
            "hal | /internal/projects/%20{checkout}/access",
            "ana | /api/orgs/3b9d5c1-0000-4000-8000-0000000000ff"})
    @Tag("AC-7.1")
    @DisplayName("AC-7.1 (BUG-2, W2) an id padded with whitespace or with a short first group is malformed")
    void aPaddedOrShortIdIsMalformed(String actor, String template) throws Exception {
        // Given: the path is sent exactly as written, so %20 and %09 reach the server percent-encoded
        String path = template.replace("{acme}", projects.acme().orgId().toString())
                .replace("{checkout}", projects.checkout().toString());
        List<Long> before = counts();

        // When
        MvcResult result = calls.as(actor, get(URI.create(path)));

        // Then
        assertThat(result.getResponse().getStatus()).as("status").isEqualTo(400);
        assertThat(JsonPath.<String>read(result.getResponse().getContentAsString(), "$.code"))
                .isEqualTo("MALFORMED_REQUEST");
        assertThat(counts()).as("nothing was stored").isEqualTo(before);
    }

    @Test
    @Tag("AC-7.2")
    @DisplayName("AC-7.2 A canonical UUID in upper case still identifies the resource")
    void aCanonicalUuidInUpperCaseStillIdentifiesTheResource() throws Exception {
        // Given
        String upperCase = projects.checkout().toString().toUpperCase(Locale.ROOT);

        // When
        MvcResult result = calls.as("ana", get(project(projects.acme().orgId(), upperCase)));

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonPath.<String>read(result.getResponse().getContentAsString(), "$.id"))
                .isEqualTo(projects.checkout().toString());
    }

    private List<Long> counts() {
        return List.of(orgMembershipRepository.count(), projectRepository.count(), projectMembershipRepository.count(),
                invitationRepository.count());
    }
}
