package com.example.authservice.qa;

import com.example.authservice.enums.ProjectRole;
import com.example.authservice.model.Project;
import com.example.authservice.model.ProjectMembership;
import com.example.authservice.model.User;
import com.example.authservice.repository.ProjectMembershipRepository;
import com.example.authservice.repository.ProjectRepository;
import com.example.authservice.support.AuthServiceIntegrationTest;
import com.example.authservice.support.ProjectFixture;
import com.example.authservice.support.ProjectFixture.Projects;
import com.jayway.jsonpath.JsonPath;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static com.example.authservice.qa.QaSupport.as;
import static com.example.authservice.qa.QaSupport.concurrently;
import static com.example.authservice.qa.QaSupport.json;
import static com.example.authservice.qa.QaSupport.statuses;
import static com.example.authservice.support.ProblemAssertions.assertProblem;
import static com.example.authservice.support.ProjectCalls.member;
import static com.example.authservice.support.ProjectCalls.members;
import static com.example.authservice.support.ProjectCalls.project;
import static com.example.authservice.support.ProjectCalls.projects;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * qa adversarial checks for D5 (archived projects are read-only, only restore changes them) and for archive and
 * restore under concurrency (P13: every project write takes the organization lock). Covers AC-3.5, AC-3.6, AC-3.8,
 * AC-7.4 beyond their Gherkin rows: every kind of admin and every write, self-service writes, and races.
 */
@AuthServiceIntegrationTest
@Import(ProjectFixture.class)
class QaArchivedProjectTest {

    private static final List<String> FIXTURE_MEMBERS = List.of(
            "cai@example.com:ADMIN", "dan@example.com:READER", "gus@example.com:MEMBER", "hal@example.com:TESTER");

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
    private String checkout;

    @BeforeEach
    void seed() {
        projects = fixture.seed();
        acme = projects.acme().orgId();
        checkout = project(acme, projects.checkout());
    }

    @ParameterizedTest(name = "qa AC-3.6: {0} {1} on archived Checkout: PROJECT_ARCHIVED")
    @CsvSource({
            "ana, RENAME", "ana, ADD", "ana, CHANGE_HAL", "ana, REMOVE_HAL",
            "ben, RENAME", "ben, ADD", "ben, CHANGE_HAL", "ben, REMOVE_HAL",
            "cai, RENAME_SAME_NAME", "cai, ADD_DAN_AS_ADMIN", "cai, ADD_EVE", "cai, CHANGE_UNKNOWN", "cai, LEAVE",
            "gus, LEAVE", "hal, LEAVE", "dan, LEAVE", "gus, CHANGE_OWN", "dan, CHANGE_OWN"})
    @Tag("AC-3.6")
    @DisplayName("qa AC-3.6 / D6: every write except restore is PROJECT_ARCHIVED, for every admin and every member")
    void everyWriteExceptRestoreIsRejectedOnAnArchivedProject(String actor, String write) throws Exception {
        // Given
        archiveAs("cai");

        // When
        MvcResult result = perform(write(write, actor), user(actor), body(write));

        // Then
        assertProblem(result, 409, "PROJECT_ARCHIVED", path(write, actor));
        Project stored = projectRepository.findById(projects.checkout()).orElseThrow();
        assertThat(stored.getName()).isEqualTo("Checkout");
        assertThat(stored.getArchivedAt()).isNotNull();
        assertThat(checkoutMembers()).containsExactlyInAnyOrderElementsOf(FIXTURE_MEMBERS);
    }

    @Test
    @Tag("AC-3.5")
    @Tag("AC-7.4")
    @DisplayName("qa AC-3.5 / AC-7.4: every read stays available to a READER on an archived project")
    void everyReadStaysAvailableOnAnArchivedProject() throws Exception {
        // Given
        String archivedAt = JsonPath.read(archiveAs("cai").getResponse().getContentAsString(), "$.archivedAt");

        // When
        MvcResult view = perform(get(checkout), projects.acme().dan(), null);
        MvcResult list = perform(get(projects(acme)), projects.acme().dan(), null);
        MvcResult memberList = perform(get(members(acme, projects.checkout())), projects.acme().dan(), null);
        MvcResult access = perform(get("/internal/projects/" + projects.checkout() + "/access"),
                projects.acme().dan(), null);

        // Then
        assertThat(view.getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonPath.<String>read(view.getResponse().getContentAsString(), "$.archivedAt")).isEqualTo(archivedAt);
        assertThat(JsonPath.<String>read(list.getResponse().getContentAsString(), "$[0].archivedAt"))
                .isEqualTo(archivedAt);
        assertThat(memberList.getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonPath.<Map<String, Object>>read(access.getResponse().getContentAsString(), "$"))
                .containsEntry("role", "READER").containsEntry("archived", true);
    }

    @Test
    @Tag("AC-3.7")
    @Tag("AC-7.4")
    @DisplayName("qa AC-3.7: after a restore every write works again and the access check says archived false")
    void afterARestoreEveryWriteWorksAgain() throws Exception {
        // Given
        archiveAs("cai");
        assertThat(perform(post(checkout + "/restore"), projects.acme().ben(), null).getResponse().getStatus())
                .isEqualTo(200);

        // When / Then
        assertThat(perform(patch(checkout), projects.acme().cai(), "{\"name\":\"Checkout Web\"}")
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(perform(post(members(acme, projects.checkout())), projects.acme().cai(),
                "{\"userId\":\"" + projects.ivy().getId() + "\",\"role\":\"READER\"}").getResponse().getStatus())
                .isEqualTo(201);
        assertThat(perform(delete(member(acme, projects.checkout(), projects.hal().getId())), projects.hal(), null)
                .getResponse().getStatus()).isEqualTo(204);
        assertThat(JsonPath.<Boolean>read(perform(get("/internal/projects/" + projects.checkout() + "/access"),
                projects.gus(), null).getResponse().getContentAsString(), "$.archived")).isFalse();
    }

    @Test
    @Tag("AC-3.8")
    @DisplayName("qa AC-3.8: a second archive after a restore sets a new archive time; the stored time is the response's")
    void archiveTimeIsTheStoredTime() throws Exception {
        // Given
        String first = JsonPath.read(archiveAs("cai").getResponse().getContentAsString(), "$.archivedAt");
        perform(post(checkout + "/restore"), projects.acme().cai(), null);
        Thread.sleep(5);

        // When
        String second = JsonPath.read(archiveAs("ana").getResponse().getContentAsString(), "$.archivedAt");

        // Then
        assertThat(second).isNotEqualTo(first);
        String stored = JsonPath.read(perform(get(checkout), projects.acme().dan(), null).getResponse()
                .getContentAsString(), "$.archivedAt");
        assertThat(stored).isEqualTo(second);
    }

    @RepeatedTest(value = 5, name = "qa AC-3.8 race: 6 simultaneous archives give one archive time (run {currentRepetition})")
    @Tag("AC-3.8")
    void simultaneousArchivesGiveOneArchiveTime() throws Exception {
        // Given
        List<Callable<MvcResult>> calls = new ArrayList<>();
        for (User admin : List.of(projects.acme().ana(), projects.acme().ben(), projects.acme().cai(),
                projects.acme().ana(), projects.acme().ben(), projects.acme().cai())) {
            calls.add(() -> perform(post(checkout + "/archive"), admin, null));
        }

        // When
        List<MvcResult> results = concurrently(calls);

        // Then
        assertThat(statuses(results)).containsOnly(200);
        List<String> times = new ArrayList<>();
        for (MvcResult result : results) {
            times.add(JsonPath.read(result.getResponse().getContentAsString(), "$.archivedAt"));
        }
        assertThat(times.stream().distinct().toList()).hasSize(1);
        String stored = JsonPath.read(perform(get(checkout), projects.acme().dan(), null).getResponse()
                .getContentAsString(), "$.archivedAt");
        assertThat(stored).isEqualTo(times.get(0));
    }

    @RepeatedTest(value = 5, name = "qa restore race: a restore and a rename of an archived project (run {currentRepetition})")
    @Tag("AC-3.6")
    @Tag("AC-3.7")
    void aRestoreRacingWithARename() throws Exception {
        // Given
        archiveAs("cai");

        // When
        List<MvcResult> results = concurrently(
                () -> perform(post(checkout + "/restore"), projects.acme().ana(), null),
                () -> perform(patch(checkout), projects.acme().cai(), "{\"name\":\"Checkout Web\"}"));

        // Then
        assertThat(results.get(0).getResponse().getStatus()).isEqualTo(200);
        MvcResult rename = results.get(1);
        Project stored = projectRepository.findById(projects.checkout()).orElseThrow();
        assertThat(stored.getArchivedAt()).isNull();
        if (rename.getResponse().getStatus() == 200) {
            assertThat(stored.getName()).isEqualTo("Checkout Web");
        } else {
            assertProblem(rename, 409, "PROJECT_ARCHIVED", checkout);
            assertThat(stored.getName()).isEqualTo("Checkout");
        }
    }

    @RepeatedTest(value = 5, name = "qa archive race: an archive and membership writes (run {currentRepetition})")
    @Tag("AC-3.6")
    void anArchiveRacingWithMembershipWrites() throws Exception {
        // Given
        String add = members(acme, projects.checkout());
        String change = member(acme, projects.checkout(), projects.gus().getId());
        String leave = member(acme, projects.checkout(), projects.hal().getId());

        // When
        List<MvcResult> results = concurrently(
                () -> perform(post(checkout + "/archive"), projects.acme().ana(), null),
                () -> perform(post(add), projects.acme().cai(),
                        "{\"userId\":\"" + projects.ivy().getId() + "\",\"role\":\"TESTER\"}"),
                () -> perform(patch(change), projects.acme().ben(), "{\"role\":\"READER\"}"),
                () -> perform(delete(leave), projects.hal(), null));

        // Then: each write either happened before the archive or got PROJECT_ARCHIVED, and the state agrees
        assertThat(results.get(0).getResponse().getStatus()).isEqualTo(200);
        assertThat(projectRepository.findById(projects.checkout()).orElseThrow().getArchivedAt()).isNotNull();
        assertOutcome(results.get(1), 201, add, roleOf(projects.ivy()), Optional.of(ProjectRole.TESTER),
                Optional.empty());
        assertOutcome(results.get(2), 200, change, roleOf(projects.gus()), Optional.of(ProjectRole.READER),
                Optional.of(ProjectRole.MEMBER));
        assertOutcome(results.get(3), 204, leave, roleOf(projects.hal()), Optional.empty(),
                Optional.of(ProjectRole.TESTER));
    }

    @RepeatedTest(value = 5, name = "qa archive/restore ping-pong: every answer is consistent (run {currentRepetition})")
    @Tag("AC-3.8")
    void archiveAndRestorePingPong() throws Exception {
        // Given
        List<Callable<MvcResult>> calls = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String action = i % 2 == 0 ? "/archive" : "/restore";
            User admin = i % 3 == 0 ? projects.acme().cai() : projects.acme().ben();
            calls.add(() -> perform(post(checkout + action), admin, null));
        }

        // When
        List<MvcResult> results = concurrently(calls);

        // Then
        assertThat(statuses(results)).containsOnly(200);
        for (int i = 0; i < results.size(); i++) {
            Object archivedAt = JsonPath.read(results.get(i).getResponse().getContentAsString(), "$.archivedAt");
            if (i % 2 == 0) {
                assertThat(archivedAt).as("archive response %d", i).isNotNull();
            } else {
                assertThat(archivedAt).as("restore response %d", i).isNull();
            }
        }
    }

    private void assertOutcome(MvcResult result, int success, String path, Optional<ProjectRole> actual,
                               Optional<ProjectRole> ifDone, Optional<ProjectRole> ifRejected) throws Exception {
        if (result.getResponse().getStatus() == success) {
            assertThat(actual).isEqualTo(ifDone);
        } else {
            assertProblem(result, 409, "PROJECT_ARCHIVED", path);
            assertThat(actual).isEqualTo(ifRejected);
        }
    }

    private MvcResult archiveAs(String actor) throws Exception {
        MvcResult result = perform(post(checkout + "/archive"), user(actor), null);
        assertThat(result.getResponse().getStatus()).as("%s archives Checkout", actor).isEqualTo(200);
        return result;
    }

    private MockHttpServletRequestBuilder write(String write, String actor) {
        String path = path(write, actor);
        return switch (write) {
            case "RENAME", "RENAME_SAME_NAME", "CHANGE_HAL", "CHANGE_UNKNOWN", "CHANGE_OWN" -> patch(path);
            case "ADD", "ADD_DAN_AS_ADMIN", "ADD_EVE" -> post(path);
            default -> delete(path);
        };
    }

    private String path(String write, String actor) {
        return switch (write) {
            case "RENAME", "RENAME_SAME_NAME" -> checkout;
            case "ADD", "ADD_DAN_AS_ADMIN", "ADD_EVE" -> members(acme, projects.checkout());
            case "CHANGE_HAL", "REMOVE_HAL" -> member(acme, projects.checkout(), projects.hal().getId());
            case "CHANGE_UNKNOWN" -> member(acme, projects.checkout(), projects.ivy().getId());
            default -> member(acme, projects.checkout(), user(actor).getId());
        };
    }

    private String body(String write) {
        return switch (write) {
            case "RENAME" -> "{\"name\":\"Checkout Web\"}";
            case "RENAME_SAME_NAME" -> "{\"name\":\"checkout\"}";
            case "ADD" -> "{\"userId\":\"" + projects.ivy().getId() + "\",\"role\":\"READER\"}";
            case "ADD_DAN_AS_ADMIN" -> "{\"userId\":\"" + projects.acme().dan().getId() + "\",\"role\":\"ADMIN\"}";
            case "ADD_EVE" -> "{\"userId\":\"" + projects.acme().eve().getId() + "\",\"role\":\"READER\"}";
            case "CHANGE_HAL", "CHANGE_UNKNOWN", "CHANGE_OWN" -> "{\"role\":\"READER\"}";
            default -> null;
        };
    }

    private User user(String name) {
        return switch (name) {
            case "ana" -> projects.acme().ana();
            case "ben" -> projects.acme().ben();
            case "cai" -> projects.acme().cai();
            case "dan" -> projects.acme().dan();
            case "gus" -> projects.gus();
            case "hal" -> projects.hal();
            default -> throw new IllegalArgumentException(name);
        };
    }

    private MvcResult perform(MockHttpServletRequestBuilder request, User actor, String body) throws Exception {
        MockHttpServletRequestBuilder withActor = as(request, actor);
        return mockMvc.perform(body == null ? withActor : json(withActor, body)).andReturn();
    }

    private Optional<ProjectRole> roleOf(User user) {
        return projectMembershipRepository.findByUserIdAndProjectId(user.getId(), projects.checkout())
                .map(ProjectMembership::getRole);
    }

    private List<String> checkoutMembers() {
        return projectMembershipRepository.findByProjectIdOrderByUserEmailAsc(projects.checkout()).stream()
                .map(membership -> membership.getUser().getEmail() + ":" + membership.getRole())
                .collect(Collectors.toList());
    }
}
