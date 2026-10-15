package com.example.authservice.controller;

import com.example.authservice.support.AuthServiceIntegrationTest;
import com.example.authservice.support.ProjectCalls;
import com.example.authservice.support.ProjectFixture;
import com.example.authservice.support.ProjectFixture.Projects;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static com.example.authservice.support.ProjectCalls.members;
import static com.example.authservice.support.ProjectCalls.project;
import static com.example.authservice.support.ProjectCalls.projects;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * US-4 AC-4.1 (phase 4, T5, D6): every timestamp in a response is a UTC timestamp, ISO-8601 with a {@code Z} offset
 * and 0 to 6 fraction digits. Background: the Projects fixture, and Ben has invited kit@example.com to Acme as MEMBER.
 */
@AuthServiceIntegrationTest
@Import(ProjectFixture.class)
class UtcTimestampsTest {

    private static final String FAY =
            "{\"email\":\"fay@example.com\",\"password\":\"Secret123!\",\"fullName\":\"Fay Lowe\"}";
    private static final String UTC_TIMESTAMP = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,6})?Z$";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectFixture fixture;

    private Projects projects;
    private ProjectCalls calls;
    private String kitToken;

    @BeforeEach
    void seedProjectsAndInviteKit() throws Exception {
        projects = fixture.seed();
        calls = new ProjectCalls(mockMvc);
        MvcResult invited = calls.as("ben", post(invitations()), "{\"email\":\"kit@example.com\",\"role\":\"MEMBER\"}");
        assertThat(invited.getResponse().getStatus()).isEqualTo(201);
        kitToken = JsonPath.read(invited.getResponse().getContentAsString(), "$.token");
    }

    @ParameterizedTest(name = "AC-4.1 {0}: {1}")
    @CsvSource(delimiter = '|', value = {
            "Fay registers                       | createdAt",
            "Ana creates an organization         | createdAt",
            "Ben invites Fay                     | createdAt, expiresAt",
            "Ben lists the pending invitations   | [0].createdAt, [0].expiresAt",
            "Ana lists the members               | [0].joinedAt",
            "Ana creates a project               | createdAt",
            "Cai archives Checkout               | createdAt, archivedAt",
            "Cai adds Ivy to Checkout            | joinedAt",
            "Kit accepts anonymously             | user.createdAt, organization.createdAt"})
    @Tag("AC-4.1")
    @DisplayName("AC-4.1 Every timestamp in a response is a UTC timestamp")
    void everyTimestampInAResponseIsAUtcTimestamp(String request, String fields) throws Exception {
        // When
        MvcResult result = perform(request);

        // Then
        assertThat(result.getResponse().getStatus()).as("status").isBetween(200, 299);
        String body = result.getResponse().getContentAsString();
        for (String field : fields.split(",")) {
            assertThat(JsonPath.<String>read(body, "$" + (field.trim().startsWith("[") ? "" : ".") + field.trim()))
                    .as(field.trim()).matches(UTC_TIMESTAMP);
        }
    }

    private MvcResult perform(String request) throws Exception {
        String acme = "/api/orgs/" + projects.acme().orgId();
        return switch (request) {
            case "Fay registers" -> mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content(FAY))
                    .andReturn();
            case "Ana creates an organization" -> calls.as("ana", post("/api/orgs"), "{\"name\":\"Time Org QA\"}");
            case "Ben invites Fay" -> calls.as("ben", post(invitations()),
                    "{\"email\":\"fay@example.com\",\"role\":\"MEMBER\"}");
            case "Ben lists the pending invitations" -> calls.as("ben", get(invitations()));
            case "Ana lists the members" -> calls.as("ana", get(acme + "/members"));
            case "Ana creates a project" -> calls.as("ana", post(projects(projects.acme().orgId())),
                    "{\"name\":\"Mobile App\"}");
            case "Cai archives Checkout" -> calls.as("cai",
                    post(project(projects.acme().orgId(), projects.checkout()) + "/archive"));
            case "Cai adds Ivy to Checkout" -> calls.as("cai", post(members(projects.acme().orgId(),
                    projects.checkout())), "{\"userId\":\"" + projects.ivy().getId() + "\",\"role\":\"READER\"}");
            case "Kit accepts anonymously" -> mockMvc.perform(post("/api/auth/invitations/" + kitToken + "/accept")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\":\"Secret123!\",\"fullName\":\"Kit Hale\"}"))
                    .andReturn();
            default -> throw new IllegalArgumentException(request);
        };
    }

    private String invitations() {
        return "/api/orgs/" + projects.acme().orgId() + "/invitations";
    }
}
