package com.example.authservice.controller;

import com.example.authservice.model.Project;
import com.example.authservice.repository.ProjectRepository;
import com.example.authservice.support.AuthServiceIntegrationTest;
import com.example.authservice.support.ProjectCalls;
import com.example.authservice.support.ProjectFixture;
import com.example.authservice.support.ProjectFixture.Projects;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static com.example.authservice.support.ProblemAssertions.assertProblem;
import static com.example.authservice.support.ProjectCalls.project;
import static com.example.authservice.support.ProjectCalls.projects;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * US-3 (T12): rename, archive and restore, plus AC-1.4 row 4 and AC-2.4, which need archive. The rename error order
 * is D5's: 400, then 403, then 409 {@code PROJECT_ARCHIVED}, then 409 {@code PROJECT_NAME_TAKEN}.
 */
@AuthServiceIntegrationTest
@Import(ProjectFixture.class)
class ProjectLifecycleTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectFixture fixture;

    @Autowired
    private ProjectRepository projectRepository;

    private Projects projects;
    private ProjectCalls calls;
    private String checkout;

    @BeforeEach
    void seedProjects() {
        projects = fixture.seed();
        calls = new ProjectCalls(mockMvc);
        checkout = project(projects.acme().orgId(), projects.checkout());
    }

    @ParameterizedTest(name = "AC-3.1 {0}: {1}")
    @CsvSource({"ana, 200", "ben, 200", "cai, 200", "gus, 403", "hal, 403", "dan, 403", "ivy, 403", "eve, 403"})
    @Tag("AC-3.1")
    void onlyEffectiveProjectAdminsCanRename(String actor, int status) throws Exception {
        // When
        MvcResult result = calls.as(actor, patch(checkout), "{\"name\":\"Checkout Web\"}");

        // Then
        if (status == 200) {
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            DocumentContext body = JsonPath.parse(result.getResponse().getContentAsString());
            assertThat(body.read("$.name", String.class)).isEqualTo("Checkout Web");
            assertThat(body.read("$.myRole", String.class)).isEqualTo("ADMIN");
            assertThat(checkoutProject().getName()).isEqualTo("Checkout Web");
            return;
        }
        assertProblem(result, 403, "FORBIDDEN", checkout);
        assertThat(checkoutProject().getName()).isEqualTo("Checkout");
    }

    @Test
    @Tag("AC-3.2")
    @DisplayName("AC-3.2 Renaming to another project's name in any case is PROJECT_NAME_TAKEN")
    void renamingToAnotherProjectsNameIsTaken() throws Exception {
        // When
        MvcResult result = calls.as("cai", patch(checkout), "{\"name\":\"billing\"}");

        // Then
        assertProblem(result, 409, "PROJECT_NAME_TAKEN", checkout);
        assertThat(checkoutProject().getName()).isEqualTo("Checkout");
    }

    @Test
    @Tag("AC-3.2")
    @DisplayName("AC-3.2 Renaming to the project's own name in another case is allowed")
    void renamingToItsOwnNameInAnotherCaseIsAllowed() throws Exception {
        // When
        MvcResult result = calls.as("cai", patch(checkout), "{\"name\":\"CHECKOUT\"}");

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(checkoutProject().getName()).isEqualTo("CHECKOUT");
    }

    @ParameterizedTest(name = "AC-3.3 body {0}")
    @ValueSource(strings = {"{}", "{\"name\":\"   \"}", "LONG"})
    @Tag("AC-3.3")
    void anInvalidNewNameIsAValidationError(String body) throws Exception {
        // Given
        String request = body.equals("LONG") ? "{\"name\":\"" + "a".repeat(101) + "\"}" : body;

        // When
        MvcResult result = calls.as("cai", patch(checkout), request);

        // Then
        DocumentContext problem = assertProblem(result, 400, "VALIDATION_FAILED", checkout);
        assertThat(problem.read("$.errors[*].field", List.class)).contains("name");
        assertThat(checkoutProject().getName()).isEqualTo("Checkout");
    }

    @ParameterizedTest(name = "AC-3.4 {0}: {1}")
    @CsvSource({"ana, 200", "cai, 200", "gus, 403", "dan, 403", "eve, 403"})
    @Tag("AC-3.4")
    void onlyEffectiveProjectAdminsCanArchive(String actor, int status) throws Exception {
        // When
        MvcResult result = calls.as(actor, post(checkout + "/archive"));

        // Then
        if (status == 200) {
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            assertThat(JsonPath.<Object>read(result.getResponse().getContentAsString(), "$.archivedAt")).isNotNull();
            assertThat(checkoutProject().getArchivedAt()).isNotNull();
            return;
        }
        assertProblem(result, 403, "FORBIDDEN", checkout + "/archive");
        assertThat(checkoutProject().getArchivedAt()).isNull();
    }

    @Test
    @Tag("AC-3.5")
    @DisplayName("AC-3.5 An archived project stays readable for everyone with access")
    void anArchivedProjectStaysReadable() throws Exception {
        // Given
        archiveCheckoutAsCai();

        // When
        MvcResult result = calls.as("dan", get(checkout));

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        DocumentContext body = JsonPath.parse(result.getResponse().getContentAsString());
        assertThat(body.read("$.myRole", String.class)).isEqualTo("READER");
        assertThat(body.read("$.archivedAt", Object.class)).isNotNull();
    }

    @Test
    @Tag("AC-3.6")
    @DisplayName("AC-3.6 row 1: renaming an archived project is PROJECT_ARCHIVED")
    void renamingAnArchivedProjectIsRejected() throws Exception {
        // Given
        archiveCheckoutAsCai();

        // When
        MvcResult result = calls.as("cai", patch(checkout), "{\"name\":\"Checkout Web\"}");

        // Then
        assertProblem(result, 409, "PROJECT_ARCHIVED", checkout);
        assertThat(checkoutProject().getName()).isEqualTo("Checkout");
    }

    @Test
    @Tag("AC-3.6")
    @DisplayName("D5 rename order: on an archived project, a name another project uses is PROJECT_ARCHIVED")
    void archivedWinsOverNameTaken() throws Exception {
        // Given
        archiveCheckoutAsCai();

        // When
        MvcResult result = calls.as("cai", patch(checkout), "{\"name\":\"Billing\"}");

        // Then
        assertProblem(result, 409, "PROJECT_ARCHIVED", checkout);
    }

    @Test
    @Tag("AC-3.6")
    @DisplayName("D5 rename order: on an archived project, a caller who isn't allowed gets 403, not 409")
    void forbiddenWinsOverArchived() throws Exception {
        // Given
        archiveCheckoutAsCai();

        // When
        MvcResult result = calls.as("gus", patch(checkout), "{\"name\":\"Billing\"}");

        // Then
        assertProblem(result, 403, "FORBIDDEN", checkout);
    }

    @Test
    @Tag("AC-3.3")
    @DisplayName("D5 rename order: an invalid body is 400, before authorization and the archived check")
    void validationWinsOverForbiddenAndArchived() throws Exception {
        // Given
        archiveCheckoutAsCai();

        // When
        MvcResult result = calls.as("gus", patch(checkout), "{\"name\":\"   \"}");

        // Then
        assertProblem(result, 400, "VALIDATION_FAILED", checkout);
    }

    @ParameterizedTest(name = "AC-3.7 {0}: {1}")
    @CsvSource({"cai, 200", "gus, 403", "eve, 403"})
    @Tag("AC-3.7")
    void onlyEffectiveProjectAdminsCanRestore(String actor, int status) throws Exception {
        // Given
        archiveCheckoutAsCai();

        // When
        MvcResult result = calls.as(actor, post(checkout + "/restore"));

        // Then
        if (status == 200) {
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            assertThat(JsonPath.<Object>read(result.getResponse().getContentAsString(), "$.archivedAt")).isNull();
            MvcResult rename = calls.as("cai", patch(checkout), "{\"name\":\"Checkout Web\"}");
            assertThat(rename.getResponse().getStatus()).isEqualTo(200);
            return;
        }
        assertProblem(result, 403, "FORBIDDEN", checkout + "/restore");
        assertThat(checkoutProject().getArchivedAt()).isNotNull();
    }

    @Test
    @Tag("AC-3.8")
    @DisplayName("AC-3.8 Archiving an archived project keeps its archive time")
    void archiveIsIdempotent() throws Exception {
        // Given
        String archivedAt = JsonPath.read(archiveCheckoutAsCai().getResponse().getContentAsString(), "$.archivedAt");

        // When
        MvcResult result = calls.as("cai", post(checkout + "/archive"));

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonPath.<String>read(result.getResponse().getContentAsString(), "$.archivedAt"))
                .isEqualTo(archivedAt);
    }

    @Test
    @Tag("AC-3.8")
    @DisplayName("AC-3.8 Restoring an active project is a no-op success")
    void restoreIsIdempotent() throws Exception {
        // When
        MvcResult result = calls.as("cai", post(checkout + "/restore"));

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonPath.<Object>read(result.getResponse().getContentAsString(), "$.archivedAt")).isNull();
    }

    @Test
    @Tag("AC-1.4")
    @DisplayName("AC-1.4 row 4: an archived project's name can't be reused")
    void anArchivedProjectsNameCannotBeReused() throws Exception {
        // Given
        String acmeProjects = projects(projects.acme().orgId());
        MvcResult archived = calls.as("ana", post(project(projects.acme().orgId(), projects.billing()) + "/archive"));
        assertThat(archived.getResponse().getStatus()).isEqualTo(200);

        // When
        MvcResult result = calls.as("ana", post(acmeProjects), "{\"name\":\"billing\"}");

        // Then
        assertProblem(result, 409, "PROJECT_NAME_TAKEN", acmeProjects);
        assertThat(projectRepository.findByOrganizationId(projects.acme().orgId())).hasSize(2);
    }

    @Test
    @Tag("AC-2.4")
    @DisplayName("AC-2.4 Archived projects are listed with their archive time")
    void archivedProjectsAreListedWithTheirArchiveTime() throws Exception {
        // Given
        calls.as("ana", post(project(projects.acme().orgId(), projects.billing()) + "/archive"));

        // When
        MvcResult result = calls.as("ana", get(projects(projects.acme().orgId())));

        // Then
        String json = result.getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(json, "$[*].name")).containsExactly("Billing", "Checkout");
        assertThat(JsonPath.<Object>read(json, "$[0].archivedAt")).isNotNull();
        assertThat(JsonPath.<Object>read(json, "$[1].archivedAt")).isNull();
    }

    private MvcResult archiveCheckoutAsCai() throws Exception {
        MvcResult result = calls.as("cai", post(checkout + "/archive"));
        assertThat(result.getResponse().getStatus()).as("Cai archives Checkout").isEqualTo(200);
        return result;
    }

    private Project checkoutProject() {
        return projectRepository.findById(projects.checkout()).orElseThrow();
    }
}
