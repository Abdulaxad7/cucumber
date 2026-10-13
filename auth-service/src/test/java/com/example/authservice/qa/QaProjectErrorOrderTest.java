package com.example.authservice.qa;

import com.example.authservice.model.Project;
import com.example.authservice.model.User;
import com.example.authservice.repository.ProjectMembershipRepository;
import com.example.authservice.repository.ProjectRepository;
import com.example.authservice.support.AcmeFixture;
import com.example.authservice.support.AuthServiceIntegrationTest;
import com.example.authservice.support.ProjectFixture;
import com.example.authservice.support.ProjectFixture.Projects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Callable;

import static com.example.authservice.qa.QaSupport.as;
import static com.example.authservice.qa.QaSupport.concurrently;
import static com.example.authservice.qa.QaSupport.json;
import static com.example.authservice.support.ProblemAssertions.assertProblem;
import static com.example.authservice.support.ProjectCalls.member;
import static com.example.authservice.support.ProjectCalls.members;
import static com.example.authservice.support.ProjectCalls.project;
import static com.example.authservice.support.ProjectCalls.projects;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * qa checks of the D6 error orders (the first failure wins) for add, change role and remove or leave, the D5
 * reservation of archived projects' names, and name uniqueness under simultaneous creates and renames with letter
 * case and whitespace variants (AC-1.4, AC-1.7, P13).
 */
@AuthServiceIntegrationTest
@Import(ProjectFixture.class)
class QaProjectErrorOrderTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectFixture fixture;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMembershipRepository projectMembershipRepository;

    private Projects projects;
    private UUID acme;

    @BeforeEach
    void seed() {
        projects = fixture.seed();
        acme = projects.acme().orgId();
    }

    @ParameterizedTest(name = "qa D6 add order: {0}")
    @CsvSource(delimiter = '|', value = {
            "member, invalid body, archived          | gus | ARCHIVED | ivy     | OWNER  | 400 | MALFORMED_REQUEST",
            "member, foreign user, archived          | gus | ARCHIVED | eve     | ADMIN  | 403 | FORBIDDEN",
            "admin, foreign user as ADMIN, archived  | cai | ARCHIVED | eve     | ADMIN  | 409 | PROJECT_ARCHIVED",
            "admin, existing member over cap, archived | ben | ARCHIVED | dan   | ADMIN  | 409 | PROJECT_ARCHIVED",
            "admin, foreign user as ADMIN            | cai | ACTIVE   | eve     | ADMIN  | 422 | NOT_ORG_MEMBER",
            "admin, unknown user as ADMIN            | ana | ACTIVE   | unknown | ADMIN  | 422 | NOT_ORG_MEMBER",
            "admin, organization READER member as ADMIN | ana | ACTIVE | dan    | ADMIN  | 409 | ALREADY_MEMBER",
            "admin, organization READER newcomer as TESTER | ana | ACTIVE | dan-billing | TESTER | 422 | PROJECT_ROLE_NOT_ALLOWED"})
    @Tag("AC-3.6")
    @Tag("AC-4.4")
    @Tag("AC-4.5")
    @Tag("AC-4.6")
    @DisplayName("qa D6: add order is 400, 403, PROJECT_ARCHIVED, NOT_ORG_MEMBER, ALREADY_MEMBER, PROJECT_ROLE_NOT_ALLOWED")
    void theAddErrorOrder(String description, String actor, String state, String target, String role, int status,
                          String code) throws Exception {
        // Given
        UUID projectId = target.equals("dan-billing") ? projects.billing() : projects.checkout();
        if (state.equals("ARCHIVED")) {
            archive(projectId);
        }
        UUID userId = switch (target) {
            case "ivy" -> projects.ivy().getId();
            case "eve" -> projects.acme().eve().getId();
            case "unknown" -> AcmeFixture.UNKNOWN_ID;
            default -> projects.acme().dan().getId();
        };
        String path = members(acme, projectId);
        long before = projectMembershipRepository.count();

        // When
        MvcResult result = perform(post(path), user(actor),
                "{\"userId\":\"" + userId + "\",\"role\":\"" + role + "\"}");

        // Then
        assertProblem(result, status, code, path);
        assertThat(projectMembershipRepository.count()).as(description).isEqualTo(before);
    }

    @ParameterizedTest(name = "qa D6 change order: {0}")
    @CsvSource(delimiter = '|', value = {
            "outsider, invalid body, archived      | eve | ARCHIVED | hal     | 7       | 400 | MALFORMED_REQUEST",
            "member, other member, archived        | gus | ARCHIVED | hal     | READER  | 403 | FORBIDDEN",
            "implicit admin, self without row, archived | ben | ARCHIVED | ben | READER  | 409 | PROJECT_ARCHIVED",
            "admin, unknown target, archived       | ana | ARCHIVED | unknown | READER  | 409 | PROJECT_ARCHIVED",
            "implicit admin, self without row      | ana | ACTIVE   | ana     | READER  | 404 | MEMBER_NOT_FOUND",
            "member, self over cap                 | dan | ACTIVE   | dan     | ADMIN   | 422 | SELF_ROLE_CHANGE",
            "admin, organization READER over cap   | cai | ACTIVE   | dan     | MEMBER  | 422 | PROJECT_ROLE_NOT_ALLOWED"})
    @Tag("AC-3.6")
    @Tag("AC-5.2")
    @Tag("AC-5.3")
    @Tag("AC-5.4")
    @DisplayName("qa D6: change order is 400, 403, PROJECT_ARCHIVED, MEMBER_NOT_FOUND, SELF_ROLE_CHANGE, cap")
    void theChangeErrorOrder(String description, String actor, String state, String target, String role, int status,
                             String code) throws Exception {
        // Given
        if (state.equals("ARCHIVED")) {
            archive(projects.checkout());
        }
        UUID userId = target.equals("unknown") ? AcmeFixture.UNKNOWN_ID : user(target).getId();
        String path = member(acme, projects.checkout(), userId);
        String body = role.equals("7") ? "{\"role\":7}" : "{\"role\":\"" + role + "\"}";
        List<String> before = checkoutRoles();

        // When
        MvcResult result = perform(patch(path), user(actor), body);

        // Then
        assertProblem(result, status, code, path);
        assertThat(checkoutRoles()).as(description).isEqualTo(before);
    }

    @ParameterizedTest(name = "qa D6 remove order: {0}")
    @CsvSource(delimiter = '|', value = {
            "member removes another, archived   | gus | ARCHIVED | hal     | 403 | FORBIDDEN",
            "outsider leaves, archived          | ivy | ARCHIVED | ivy     | 403 | FORBIDDEN",
            "admin removes unknown, archived    | cai | ARCHIVED | unknown | 409 | PROJECT_ARCHIVED",
            "implicit admin leaves, archived    | ben | ARCHIVED | ben     | 409 | PROJECT_ARCHIVED",
            "admin removes unknown              | cai | ACTIVE   | unknown | 404 | MEMBER_NOT_FOUND",
            "admin removes organization member without row | cai | ACTIVE | ivy | 404 | MEMBER_NOT_FOUND"})
    @Tag("AC-3.6")
    @Tag("AC-5.6")
    @Tag("AC-5.9")
    @DisplayName("qa D6: remove and leave order is 403, PROJECT_ARCHIVED, MEMBER_NOT_FOUND")
    void theRemoveErrorOrder(String description, String actor, String state, String target, int status, String code)
            throws Exception {
        // Given
        if (state.equals("ARCHIVED")) {
            archive(projects.checkout());
        }
        UUID userId = target.equals("unknown") ? AcmeFixture.UNKNOWN_ID : user(target).getId();
        String path = member(acme, projects.checkout(), userId);
        List<String> before = checkoutRoles();

        // When
        MvcResult result = perform(delete(path), user(actor), null);

        // Then
        assertProblem(result, status, code, path);
        assertThat(checkoutRoles()).as(description).isEqualTo(before);
    }

    @Test
    @Tag("AC-1.4")
    @Tag("AC-3.2")
    @DisplayName("qa D5: an archived project's name stays reserved for creates and renames, in any case and spacing")
    void anArchivedProjectsNameStaysReserved() throws Exception {
        // Given
        archive(projects.billing());
        String acmeProjects = projects(acme);
        String checkout = project(acme, projects.checkout());

        // When
        MvcResult create = perform(post(acmeProjects), projects.acme().ben(), "{\"name\":\"  BILLING \\t\"}");
        MvcResult rename = perform(patch(checkout), projects.acme().cai(), "{\"name\":\"billing\"}");

        // Then
        assertProblem(create, 409, "PROJECT_NAME_TAKEN", acmeProjects);
        assertProblem(rename, 409, "PROJECT_NAME_TAKEN", checkout);
        assertThat(projectRepository.findById(projects.checkout()).orElseThrow().getName()).isEqualTo("Checkout");
        assertThat(projectRepository.findByOrganizationId(acme)).hasSize(2);
    }

    @RepeatedTest(value = 5, name = "qa AC-1.7: simultaneous creates of one name in different case and spacing (run {currentRepetition})")
    @Tag("AC-1.7")
    @Tag("AC-1.4")
    void simultaneousCreatesOfOneNameInDifferentCaseAndSpacing() throws Exception {
        // Given
        String acmeProjects = projects(acme);
        List<String> variants = List.of("Mobile QA", "MOBILE QA", " mobile qa ", "Mobile QA\\t", "mObIlE qA", "MOBILE qa");
        List<Callable<MvcResult>> calls = new ArrayList<>();
        for (int i = 0; i < variants.size(); i++) {
            User admin = i % 2 == 0 ? projects.acme().ana() : projects.acme().ben();
            String body = "{\"name\":\"" + variants.get(i) + "\"}";
            calls.add(() -> perform(post(acmeProjects), admin, body));
        }

        // When
        List<MvcResult> results = concurrently(calls);

        // Then
        assertThat(results).filteredOn(result -> result.getResponse().getStatus() == 201).hasSize(1);
        for (MvcResult loser : results.stream().filter(result -> result.getResponse().getStatus() != 201).toList()) {
            assertProblem(loser, 409, "PROJECT_NAME_TAKEN", acmeProjects);
        }
        assertThat(projectRepository.findByOrganizationId(acme)).extracting(Project::getName)
                .filteredOn(name -> name.trim().toLowerCase(Locale.ROOT).equals("mobile qa")).hasSize(1);
    }

    @RepeatedTest(value = 5, name = "qa AC-1.7 / AC-3.2: a create and two renames racing for one name (run {currentRepetition})")
    @Tag("AC-1.7")
    @Tag("AC-3.2")
    void aCreateAndTwoRenamesRacingForOneName() throws Exception {
        // When
        List<MvcResult> results = concurrently(
                () -> perform(post(projects(acme)), projects.acme().ana(), "{\"name\":\"Shared QA\"}"),
                () -> perform(patch(project(acme, projects.checkout())), projects.acme().cai(), "{\"name\":\"shared qa\"}"),
                () -> perform(patch(project(acme, projects.billing())), projects.acme().ben(), "{\"name\":\"SHARED QA\"}"));

        // Then
        assertThat(results).filteredOn(result -> result.getResponse().getStatus() / 100 == 2).hasSize(1);
        for (MvcResult loser : results.stream().filter(result -> result.getResponse().getStatus() / 100 != 2).toList()) {
            assertThat(loser.getResponse().getStatus()).isEqualTo(409);
            assertThat(QaSupport.read(loser, "$.code")).isEqualTo("PROJECT_NAME_TAKEN");
        }
        assertThat(projectRepository.findByOrganizationId(acme)).extracting(Project::getName)
                .filteredOn(name -> name.toLowerCase(Locale.ROOT).equals("shared qa")).hasSize(1);
    }

    private void archive(UUID projectId) throws Exception {
        MvcResult result = perform(post(project(acme, projectId) + "/archive"), projects.acme().ana(), null);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    private List<String> checkoutRoles() {
        return projectMembershipRepository.findByProjectIdOrderByUserEmailAsc(projects.checkout()).stream()
                .map(membership -> membership.getUser().getEmail() + ":" + membership.getRole()).toList();
    }

    private User user(String name) {
        return switch (name) {
            case "ana" -> projects.acme().ana();
            case "ben" -> projects.acme().ben();
            case "cai" -> projects.acme().cai();
            case "dan" -> projects.acme().dan();
            case "eve" -> projects.acme().eve();
            case "gus" -> projects.gus();
            case "hal" -> projects.hal();
            case "ivy" -> projects.ivy();
            default -> throw new IllegalArgumentException(name);
        };
    }

    private MvcResult perform(MockHttpServletRequestBuilder request, User actor, String body) throws Exception {
        MockHttpServletRequestBuilder withActor = as(request, actor);
        return mockMvc.perform(body == null ? withActor : json(withActor, body)).andReturn();
    }
}
