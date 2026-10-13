package com.example.authservice.controller;

import com.example.authservice.model.Project;
import com.example.authservice.repository.ProjectMembershipRepository;
import com.example.authservice.repository.ProjectRepository;
import com.example.authservice.support.AcmeFixture;
import com.example.authservice.support.AuthServiceIntegrationTest;
import com.example.authservice.support.ProjectCalls;
import com.example.authservice.support.ProjectFixture;
import com.example.authservice.support.ProjectFixture.Projects;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static com.example.authservice.support.ProblemAssertions.assertProblem;
import static com.example.authservice.support.ProjectCalls.project;
import static com.example.authservice.support.ProjectCalls.projects;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * US-1 and US-2 (T11): create, list and view projects under {@code /api/orgs/{orgId}/projects}, against the
 * Projects fixture.
 */
@AuthServiceIntegrationTest
@Import(ProjectFixture.class)
class ProjectsTest {

    private static final String UUID_PATTERN = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectFixture fixture;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMembershipRepository projectMembershipRepository;

    private Projects projects;
    private ProjectCalls calls;
    private String acmeProjects;

    @BeforeEach
    void seedProjects() {
        projects = fixture.seed();
        calls = new ProjectCalls(mockMvc);
        acmeProjects = projects(projects.acme().orgId());
    }

    @Test
    @Tag("AC-1.1")
    @DisplayName("AC-1.1 An organization admin creates a project and administers it implicitly")
    void anOrganizationAdminCreatesAProject() throws Exception {
        // When
        MvcResult result = calls.as("ben", post(acmeProjects), "{\"name\":\"Mobile App\"}");

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        DocumentContext body = JsonPath.parse(result.getResponse().getContentAsString());
        String id = body.read("$.id");
        assertThat(id).matches(UUID_PATTERN);
        assertThat(body.read("$.orgId", String.class)).isEqualTo(projects.acme().orgId().toString());
        assertThat(body.read("$.name", String.class)).isEqualTo("Mobile App");
        assertThat(body.read("$.createdAt", String.class)).isNotBlank();
        assertThat(body.read("$.archivedAt", Object.class)).isNull();
        assertThat(body.read("$.myRole", String.class)).isEqualTo("ADMIN");
        assertThat(body.read("$", Map.class)).containsOnlyKeys(
                "id", "orgId", "name", "createdAt", "archivedAt", "myRole");
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo(acmeProjects + "/" + id);
        assertThat(projectMembershipRepository.findAll())
                .noneMatch(membership -> membership.getProject().getId().toString().equals(id));
    }

    @ParameterizedTest(name = "AC-1.2 {0}: {1}")
    @CsvSource({"ana, 201", "ben, 201", "cai, 403", "gus, 403", "dan, 403", "eve, 403"})
    @Tag("AC-1.2")
    void onlyOrganizationOwnersAndAdminsCanCreateProjects(String actor, int status) throws Exception {
        // When
        MvcResult result = calls.as(actor, post(acmeProjects), "{\"name\":\"Mobile App\"}");

        // Then
        if (status == 201) {
            assertThat(result.getResponse().getStatus()).isEqualTo(201);
            return;
        }
        assertProblem(result, 403, "FORBIDDEN", acmeProjects);
        assertThat(acmeProjectNames()).doesNotContain("Mobile App");
    }

    @ParameterizedTest(name = "AC-1.3 body {0}")
    @ValueSource(strings = {"{}", "{\"name\":\"\"}", "{\"name\":\"   \"}", "LONG"})
    @Tag("AC-1.3")
    void anInvalidProjectNameIsAValidationError(String body) throws Exception {
        // Given
        String request = body.equals("LONG") ? "{\"name\":\"" + "a".repeat(101) + "\"}" : body;

        // When
        MvcResult result = calls.as("ana", post(acmeProjects), request);

        // Then
        DocumentContext problem = assertProblem(result, 400, "VALIDATION_FAILED", acmeProjects);
        assertThat(problem.read("$.errors[*].field", List.class)).contains("name");
        assertThat(acmeProjectNames()).containsExactlyInAnyOrder("Billing", "Checkout");
    }

    @Test
    @Tag("AC-1.3")
    @DisplayName("AC-1.3 / D5 Q3: the length is counted before trimming, so 100 characters and a space is 400")
    void theNameLengthIsCountedBeforeTrimming() throws Exception {
        // When
        MvcResult result = calls.as("ana", post(acmeProjects), "{\"name\":\" " + "a".repeat(100) + "\"}");

        // Then
        DocumentContext problem = assertProblem(result, 400, "VALIDATION_FAILED", acmeProjects);
        assertThat(problem.read("$.errors[*].field", List.class)).contains("name");
        assertThat(acmeProjectNames()).containsExactlyInAnyOrder("Billing", "Checkout");
    }

    @Test
    @Tag("AC-1.3")
    @DisplayName("AC-1.3 boundary: exactly 100 characters is accepted")
    void exactlyOneHundredCharactersIsAccepted() throws Exception {
        // When
        MvcResult result = calls.as("ana", post(acmeProjects), "{\"name\":\"" + "a".repeat(100) + "\"}");

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
    }

    @ParameterizedTest(name = "AC-1.4 name \"{0}\"")
    @ValueSource(strings = {"Checkout", "CHECKOUT", "  checkout  "})
    @Tag("AC-1.4")
    void aProjectNameCannotRepeatInsideOneOrganization(String name) throws Exception {
        // When
        MvcResult result = calls.as("ana", post(acmeProjects), "{\"name\":\"" + name + "\"}");

        // Then
        assertProblem(result, 409, "PROJECT_NAME_TAKEN", acmeProjects);
        assertThat(acmeProjectNames()).hasSize(2);
    }

    @Test
    @Tag("AC-1.5")
    @DisplayName("AC-1.5 The same name is allowed in another organization")
    void theSameNameIsAllowedInAnotherOrganization() throws Exception {
        // When
        MvcResult result = calls.as("eve", post(projects(projects.initech())), "{\"name\":\"Checkout\"}");

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(JsonPath.<String>read(result.getResponse().getContentAsString(), "$.orgId"))
                .isEqualTo(projects.initech().toString());
    }

    @ParameterizedTest(name = "AC-1.6 org {0}: {1} {2}")
    @CsvSource({"UNKNOWN, 403, FORBIDDEN", "INITECH, 403, FORBIDDEN", "not-a-uuid, 400, MALFORMED_REQUEST"})
    @Tag("AC-1.6")
    void anUnknownOrMalformedOrganizationIdRevealsNothing(String id, int status, String code) throws Exception {
        // Given
        String path = projects(orgId(id));

        // When
        MvcResult result = calls.as("ana", post(path), "{\"name\":\"Mobile App\"}");

        // Then
        assertProblem(result, status, code, path);
    }

    @RepeatedTest(value = 5, name = "AC-1.7 Simultaneous creations with one name create exactly one project (run {currentRepetition})")
    @Tag("AC-1.7")
    void simultaneousCreationsWithOneNameCreateExactlyOneProject() throws Exception {
        // Given
        calls.login("ana", "ben");
        List<Callable<MvcResult>> creations = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String actor = i % 2 == 0 ? "ana" : "ben";
            creations.add(() -> calls.as(actor, post(acmeProjects), "{\"name\":\"Mobile App\"}"));
        }

        // When
        List<MvcResult> results = ProjectCalls.simultaneously(creations);

        // Then
        assertThat(results).filteredOn(result -> result.getResponse().getStatus() == 201).hasSize(1);
        for (MvcResult loser : results.stream().filter(result -> result.getResponse().getStatus() != 201).toList()) {
            assertProblem(loser, 409, "PROJECT_NAME_TAKEN", acmeProjects);
        }
        assertThat(acmeProjectNames()).filteredOn("Mobile App"::equals).hasSize(1);
    }

    @Test
    @Tag("AC-1.8")
    @DisplayName("AC-1.8 The name is stored without surrounding whitespace")
    void theNameIsStoredTrimmed() throws Exception {
        // When
        MvcResult result = calls.as("ana", post(acmeProjects), "{\"name\":\"  Mobile App  \"}");

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(JsonPath.<String>read(result.getResponse().getContentAsString(), "$.name")).isEqualTo("Mobile App");
        assertThat(acmeProjectNames()).contains("Mobile App");
    }

    @ParameterizedTest(name = "AC-2.1 {0}: {1}")
    @CsvSource(delimiter = '|', value = {
            "ana | Billing:ADMIN,Checkout:ADMIN",
            "ben | Billing:ADMIN,Checkout:ADMIN",
            "cai | Checkout:ADMIN",
            "gus | Checkout:MEMBER",
            "hal | Checkout:TESTER",
            "dan | Checkout:READER",
            "ivy | ''",
            "eve | FORBIDDEN"})
    @Tag("AC-2.1")
    void eachMemberSeesOnlyTheProjectsTheyHaveAccessTo(String actor, String expected) throws Exception {
        // When
        MvcResult result = calls.as(actor, get(acmeProjects));

        // Then
        if (expected.equals("FORBIDDEN")) {
            assertProblem(result, 403, "FORBIDDEN", acmeProjects);
            return;
        }
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        List<String> listed = new ArrayList<>();
        String json = result.getResponse().getContentAsString();
        int size = JsonPath.<Integer>read(json, "$.length()");
        for (int i = 0; i < size; i++) {
            listed.add(JsonPath.read(json, "$[" + i + "].name") + ":" + JsonPath.read(json, "$[" + i + "].myRole"));
        }
        assertThat(listed).containsExactlyElementsOf(expected.isEmpty() ? List.of() : List.of(expected.split(",")));
    }

    @Test
    @Tag("AC-2.2")
    @DisplayName("AC-2.2 Ana: the project view has every field and myRole ADMIN")
    void viewingAProjectShowsEveryField() throws Exception {
        // Given
        String path = project(projects.acme().orgId(), projects.checkout());

        // When
        MvcResult result = calls.as("ana", get(path));

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        DocumentContext body = JsonPath.parse(result.getResponse().getContentAsString());
        assertThat(body.read("$.id", String.class)).isEqualTo(projects.checkout().toString());
        assertThat(body.read("$.orgId", String.class)).isEqualTo(projects.acme().orgId().toString());
        assertThat(body.read("$.name", String.class)).isEqualTo("Checkout");
        assertThat(body.read("$.createdAt", String.class)).isNotBlank();
        assertThat(body.read("$.archivedAt", Object.class)).isNull();
        assertThat(body.read("$.myRole", String.class)).isEqualTo("ADMIN");
    }

    @ParameterizedTest(name = "AC-2.2 {0}: {1}")
    @CsvSource({"ben, ADMIN", "cai, ADMIN", "gus, MEMBER", "hal, TESTER", "dan, READER", "ivy, FORBIDDEN",
            "eve, FORBIDDEN"})
    @Tag("AC-2.2")
    void viewingAProjectShowsTheCallersEffectiveRole(String actor, String outcome) throws Exception {
        // Given
        String path = project(projects.acme().orgId(), projects.checkout());

        // When
        MvcResult result = calls.as(actor, get(path));

        // Then
        if (outcome.equals("FORBIDDEN")) {
            assertProblem(result, 403, "FORBIDDEN", path);
            return;
        }
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonPath.<String>read(result.getResponse().getContentAsString(), "$.myRole")).isEqualTo(outcome);
    }

    @ParameterizedTest(name = "AC-2.3 {0} GET {1}/{2}: {3} {4}")
    @CsvSource({
            "cai, ACME, BILLING, 403, FORBIDDEN",
            "ana, ACME, PORTAL, 403, FORBIDDEN",
            "eve, INITECH, CHECKOUT, 403, FORBIDDEN",
            "ana, ACME, UNKNOWN, 403, FORBIDDEN",
            "ana, ACME, not-a-uuid, 400, MALFORMED_REQUEST"})
    @Tag("AC-2.3")
    void aProjectWithoutAccessOfAnotherOrganizationOrUnknownRevealsNothing(
            String actor, String org, String projectId, int status, String code) throws Exception {
        // Given
        String path = project(orgId(org), projectId(projectId));

        // When
        MvcResult result = calls.as(actor, get(path));

        // Then
        assertProblem(result, status, code, path);
    }

    private List<String> acmeProjectNames() {
        return projectRepository.findAll().stream()
                .filter(project -> project.getOrganization().getId().equals(projects.acme().orgId()))
                .map(Project::getName)
                .toList();
    }

    private Object orgId(String id) {
        return switch (id) {
            case "ACME" -> projects.acme().orgId();
            case "INITECH" -> projects.initech();
            case "UNKNOWN" -> AcmeFixture.UNKNOWN_ID;
            default -> id;
        };
    }

    private Object projectId(String id) {
        return switch (id) {
            case "CHECKOUT" -> projects.checkout();
            case "BILLING" -> projects.billing();
            case "PORTAL" -> projects.portal();
            case "UNKNOWN" -> AcmeFixture.UNKNOWN_ID;
            default -> id;
        };
    }
}
