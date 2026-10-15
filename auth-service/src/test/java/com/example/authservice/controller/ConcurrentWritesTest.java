package com.example.authservice.controller;

import com.example.authservice.model.Project;
import com.example.authservice.repository.ProjectMembershipRepository;
import com.example.authservice.repository.ProjectRepository;
import com.example.authservice.repository.UserRepository;
import com.example.authservice.support.AuthServiceIntegrationTest;
import com.example.authservice.support.ProjectCalls;
import com.example.authservice.support.ProjectFixture;
import com.example.authservice.support.ProjectFixture.Projects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

import static com.example.authservice.support.ProblemAssertions.assertProblem;
import static com.example.authservice.support.ProjectCalls.members;
import static com.example.authservice.support.ProjectCalls.projects;
import static com.example.authservice.support.ProjectCalls.simultaneously;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * US-3 AC-3.3 to AC-3.5 (phase 4, T3): races on PostgreSQL. Each scenario is repeated 5 times against a freshly seeded
 * Projects fixture; the requests start at the same moment.
 */
@AuthServiceIntegrationTest
@Import(ProjectFixture.class)
@ExtendWith(OutputCaptureExtension.class)
class ConcurrentWritesTest {

    private static final String RACE_EMAIL = "race-qa@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectFixture fixture;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMembershipRepository projectMembershipRepository;

    private Projects projects;
    private ProjectCalls calls;

    @BeforeEach
    void seedProjects() {
        projects = fixture.seed();
        calls = new ProjectCalls(mockMvc);
    }

    @RepeatedTest(value = 5, name = "AC-3.3 a registration race has one winner and leaks no email ({currentRepetition}/5)")
    @Tag("AC-3.3")
    @DisplayName("AC-3.3 A registration race on PostgreSQL has one winner and leaks no email")
    void aRegistrationRaceHasOneWinnerAndLeaksNoEmail(CapturedOutput output) throws Exception {
        // Given
        int logStart = output.getAll().length();

        // When
        List<MvcResult> results = simultaneously(List.of(register("Race-QA@Example.com"), register(RACE_EMAIL)));

        // Then
        List<MvcResult> created = results.stream().filter(r -> r.getResponse().getStatus() == 201).toList();
        List<MvcResult> rejected = results.stream().filter(r -> r.getResponse().getStatus() != 201).toList();
        assertThat(created).hasSize(1);
        assertThat(rejected).singleElement().satisfies(result -> {
            assertProblem(result, 409, "EMAIL_ALREADY_REGISTERED", "/api/auth/register");
            assertThat(result.getResponse().getContentAsString().toLowerCase(Locale.ROOT)).doesNotContain(RACE_EMAIL);
        });
        assertThat(userRepository.findAll()).filteredOn(user -> user.getEmail().equals(RACE_EMAIL)).hasSize(1);
        assertThat(output.getAll().substring(logStart).toLowerCase(Locale.ROOT)).doesNotContain(RACE_EMAIL);
    }

    @RepeatedTest(value = 5, name = "AC-3.4 five simultaneous creations of one name create one project "
            + "({currentRepetition}/5)")
    @Tag("AC-3.4")
    @DisplayName("AC-3.4 Simultaneous project creations with one name create exactly one project")
    void simultaneousProjectCreationsWithOneNameCreateOneProject() throws Exception {
        // Given
        String acmeProjects = projects(projects.acme().orgId());
        calls.login("ana", "ben");
        List<Callable<MvcResult>> creations = new ArrayList<>();
        for (String actor : List.of("ana", "ben", "ana", "ben", "ana")) {
            creations.add(() -> calls.as(actor, post(acmeProjects), "{\"name\":\"Mobile App\"}"));
        }

        // When
        List<MvcResult> results = simultaneously(creations);

        // Then
        assertThat(results).filteredOn(r -> r.getResponse().getStatus() == 201).hasSize(1);
        for (MvcResult result : results.stream().filter(r -> r.getResponse().getStatus() != 201).toList()) {
            assertProblem(result, 409, "PROJECT_NAME_TAKEN", acmeProjects);
        }
        assertThat(projectRepository.findByOrganizationId(projects.acme().orgId())).extracting(Project::getName)
                .filteredOn(name -> name.equals("Mobile App")).hasSize(1);
    }

    @RepeatedTest(value = 5, name = "AC-3.5 simultaneous adds of one user create one membership ({currentRepetition}/5)")
    @Tag("AC-3.5")
    @DisplayName("AC-3.5 Simultaneous adds of one user create exactly one membership")
    void simultaneousAddsOfOneUserCreateOneMembership() throws Exception {
        // Given
        String checkoutMembers = members(projects.acme().orgId(), projects.checkout());
        String body = "{\"userId\":\"" + projects.ivy().getId() + "\",\"role\":\"READER\"}";
        calls.login("ana", "cai");

        // When
        List<MvcResult> results = simultaneously(List.of(
                () -> calls.as("ana", post(checkoutMembers), body),
                () -> calls.as("cai", post(checkoutMembers), body)));

        // Then
        assertThat(results).noneMatch(r -> r.getResponse().getStatus() >= 500);
        assertThat(results).filteredOn(r -> r.getResponse().getStatus() == 201).hasSize(1);
        assertThat(results).filteredOn(r -> r.getResponse().getStatus() != 201).singleElement()
                .satisfies(result -> assertProblem(result, 409, "ALREADY_MEMBER", checkoutMembers));
        assertThat(projectMembershipRepository.findByUserIdAndProjectId(projects.ivy().getId(), projects.checkout()))
                .isPresent();
        assertThat(projectMembershipRepository.findAll())
                .filteredOn(m -> m.getUser().getId().equals(projects.ivy().getId())
                        && m.getProject().getId().equals(projects.checkout()))
                .hasSize(1);
    }

    private Callable<MvcResult> register(String email) {
        return () -> mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Secret123!\",\"fullName\":\"Race QA\"}"))
                .andReturn();
    }
}
