package com.example.authservice.controller;

import com.example.authservice.enums.ProjectRole;
import com.example.authservice.model.ProjectMembership;
import com.example.authservice.model.User;
import com.example.authservice.repository.ProjectMembershipRepository;
import com.example.authservice.support.AcmeFixture;
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
import java.util.Map;
import java.util.Optional;

import static com.example.authservice.support.ProblemAssertions.assertProblem;
import static com.example.authservice.support.ProjectCalls.member;
import static com.example.authservice.support.ProjectCalls.project;
import static com.example.authservice.support.ProjectCalls.projects;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * US-5 (T14): change project roles, remove members and leave, and AC-3.6 rows 3–5. Error orders are D6's: change
 * 400, 403, 409 {@code PROJECT_ARCHIVED}, 404 {@code MEMBER_NOT_FOUND}, 422 {@code SELF_ROLE_CHANGE}, 422
 * {@code PROJECT_ROLE_NOT_ALLOWED}; remove or leave 403, 409 {@code PROJECT_ARCHIVED}, 404 {@code MEMBER_NOT_FOUND}.
 */
@AuthServiceIntegrationTest
@Import(ProjectFixture.class)
class ProjectMemberChangesTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectFixture fixture;

    @Autowired
    private ProjectMembershipRepository projectMembershipRepository;

    private Projects projects;
    private ProjectCalls calls;
    private String checkout;

    @BeforeEach
    void seedProjects() {
        projects = fixture.seed();
        calls = new ProjectCalls(mockMvc);
        checkout = project(projects.acme().orgId(), projects.checkout());
    }

    @ParameterizedTest(name = "AC-5.1 {0} sets Hal to {1}: {2}")
    @CsvSource({"ana, MEMBER, 200", "ben, READER, 200", "cai, ADMIN, 200", "gus, READER, 403", "dan, READER, 403",
            "eve, READER, 403"})
    @Tag("AC-5.1")
    void onlyEffectiveProjectAdminsCanChangeRoles(String actor, ProjectRole newRole, int status) throws Exception {
        // Given
        String path = memberPath(projects.hal());

        // When
        MvcResult result = calls.as(actor, patch(path), roleBody(newRole.name()));

        // Then
        if (status == 200) {
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            DocumentContext body = JsonPath.parse(result.getResponse().getContentAsString());
            assertThat(body.read("$.userId", String.class)).isEqualTo(projects.hal().getId().toString());
            assertThat(body.read("$.role", String.class)).isEqualTo(newRole.name());
            assertThat(roleOf(projects.hal())).contains(newRole);
            return;
        }
        assertProblem(result, 403, "FORBIDDEN", path);
        assertThat(roleOf(projects.hal())).contains(ProjectRole.TESTER);
    }

    @ParameterizedTest(name = "AC-5.2 {0} changes their own role")
    @CsvSource({"cai, ADMIN", "gus, MEMBER"})
    @Tag("AC-5.2")
    void nobodyCanChangeTheirOwnProjectRole(String actor, ProjectRole currentRole) throws Exception {
        // Given
        User self = user(actor);
        String path = memberPath(self);

        // When
        MvcResult result = calls.as(actor, patch(path), roleBody("READER"));

        // Then
        assertProblem(result, 422, "SELF_ROLE_CHANGE", path);
        assertThat(roleOf(self)).contains(currentRole);
    }

    @ParameterizedTest(name = "AC-5.3 target {0}")
    @ValueSource(strings = {"ivy", "ben", "UNKNOWN"})
    @Tag("AC-5.3")
    void theTargetMustBeAnExplicitMember(String target) throws Exception {
        // Given
        String path = target.equals("UNKNOWN")
                ? member(projects.acme().orgId(), projects.checkout(), AcmeFixture.UNKNOWN_ID)
                : memberPath(user(target));

        // When
        MvcResult result = calls.as("ana", patch(path), roleBody("READER"));

        // Then
        assertProblem(result, 404, "MEMBER_NOT_FOUND", path);
    }

    @Test
    @Tag("AC-5.4")
    @DisplayName("AC-5.4 A role change can't exceed the organization role's cap")
    void aRoleChangeCannotExceedTheCap() throws Exception {
        // Given
        String path = memberPath(projects.acme().dan());

        // When
        MvcResult result = calls.as("ana", patch(path), roleBody("TESTER"));

        // Then
        assertProblem(result, 422, "PROJECT_ROLE_NOT_ALLOWED", path);
        assertThat(roleOf(projects.acme().dan())).contains(ProjectRole.READER);
    }

    @Test
    @Tag("AC-5.5")
    @DisplayName("AC-5.5 A demotion takes effect at once, even for a token issued earlier")
    void aDemotionTakesEffectAtOnce() throws Exception {
        // Given
        String caiToken = calls.tokenOf("cai");
        MvcResult demoted = calls.as("ana", patch(memberPath(projects.acme().cai())), roleBody("READER"));
        assertThat(demoted.getResponse().getStatus()).isEqualTo(200);

        // When
        MvcResult result = calls.withToken(caiToken, patch(checkout)
                .contentType("application/json").content("{\"name\":\"Checkout Web\"}"));

        // Then
        assertProblem(result, 403, "FORBIDDEN", checkout);
    }

    @ParameterizedTest(name = "AC-5.6 {0} removes Hal: {1}")
    @CsvSource({"ana, 204", "ben, 204", "cai, 204", "gus, 403", "dan, 403", "eve, 403"})
    @Tag("AC-5.6")
    void onlyEffectiveProjectAdminsCanRemoveOthers(String actor, int status) throws Exception {
        // Given
        String path = memberPath(projects.hal());

        // When
        MvcResult result = calls.as(actor, delete(path));

        // Then
        if (status == 204) {
            assertThat(result.getResponse().getStatus()).isEqualTo(204);
            assertThat(roleOf(projects.hal())).isEmpty();
            return;
        }
        assertProblem(result, 403, "FORBIDDEN", path);
        assertThat(roleOf(projects.hal())).contains(ProjectRole.TESTER);
    }

    @ParameterizedTest(name = "AC-5.7 {0} leaves")
    @ValueSource(strings = {"cai", "gus", "hal", "dan"})
    @Tag("AC-5.7")
    void anyExplicitMemberCanLeave(String actor) throws Exception {
        // When
        MvcResult result = calls.as(actor, delete(memberPath(user(actor))));

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(roleOf(user(actor))).isEmpty();
    }

    @Test
    @Tag("AC-5.8")
    @DisplayName("AC-5.8 A removed member loses access at once")
    void aRemovedMemberLosesAccessAtOnce() throws Exception {
        // Given
        String halToken = calls.tokenOf("hal");
        assertThat(calls.as("cai", delete(memberPath(projects.hal()))).getResponse().getStatus()).isEqualTo(204);

        // When
        MvcResult result = calls.withToken(halToken, get(checkout));

        // Then
        assertProblem(result, 403, "FORBIDDEN", checkout);
        MvcResult list = calls.withToken(halToken, get(projects(projects.acme().orgId())));
        assertThat(list.getResponse().getStatus()).isEqualTo(200);
        assertThat(list.getResponse().getContentAsString()).isEqualTo("[]");
    }

    @ParameterizedTest(name = "AC-5.9 {0} removes Ben")
    @ValueSource(strings = {"ana", "ben"})
    @Tag("AC-5.9")
    void implicitAdminsHaveNoMembershipToRemove(String actor) throws Exception {
        // Given
        String path = memberPath(projects.acme().ben());

        // When
        MvcResult result = calls.as(actor, delete(path));

        // Then
        assertProblem(result, 404, "MEMBER_NOT_FOUND", path);
        MvcResult view = calls.as("ben", get(checkout));
        assertThat(JsonPath.<String>read(view.getResponse().getContentAsString(), "$.myRole")).isEqualTo("ADMIN");
    }

    @Test
    @Tag("AC-5.10")
    @DisplayName("AC-5.10 Setting the role a member already has is a no-op success")
    void settingTheSameRoleIsANoOp() throws Exception {
        // When
        MvcResult result = calls.as("ana", patch(memberPath(projects.gus())), roleBody("MEMBER"));

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(roleOf(projects.gus())).contains(ProjectRole.MEMBER);
    }

    @ParameterizedTest(name = "AC-3.6 {0}: {1} {2}")
    @CsvSource({"cai, PATCH, hal", "cai, DELETE, hal", "hal, DELETE, hal"})
    @Tag("AC-3.6")
    void anArchivedProjectRejectsMembershipChanges(String actor, String method, String target) throws Exception {
        // Given
        archiveCheckoutAsCai();
        String path = memberPath(user(target));

        // When
        MvcResult result = method.equals("PATCH")
                ? calls.as(actor, patch(path), roleBody("MEMBER"))
                : calls.as(actor, delete(path));

        // Then
        assertProblem(result, 409, "PROJECT_ARCHIVED", path);
        assertThat(memberRoles()).containsExactlyInAnyOrder(
                "cai@example.com:ADMIN", "dan@example.com:READER", "gus@example.com:MEMBER", "hal@example.com:TESTER");
    }

    @Test
    @Tag("AC-3.6")
    @DisplayName("D6 change order: on an archived project, a caller who isn't allowed gets 403")
    void forbiddenWinsOverArchivedOnChange() throws Exception {
        // Given
        archiveCheckoutAsCai();
        String path = memberPath(projects.hal());

        // When / Then
        assertProblem(calls.as("gus", patch(path), roleBody("READER")), 403, "FORBIDDEN", path);
    }

    @Test
    @Tag("AC-3.6")
    @DisplayName("D6 change order: PROJECT_ARCHIVED wins over MEMBER_NOT_FOUND")
    void archivedWinsOverMemberNotFound() throws Exception {
        // Given
        archiveCheckoutAsCai();
        String path = memberPath(projects.ivy());

        // When / Then
        assertProblem(calls.as("ana", patch(path), roleBody("READER")), 409, "PROJECT_ARCHIVED", path);
    }

    @Test
    @Tag("AC-5.2")
    @DisplayName("D6 change order: SELF_ROLE_CHANGE wins over PROJECT_ROLE_NOT_ALLOWED (Dan to TESTER)")
    void selfRoleChangeWinsOverTheCap() throws Exception {
        // Given
        String path = memberPath(projects.acme().dan());

        // When / Then
        assertProblem(calls.as("dan", patch(path), roleBody("TESTER")), 422, "SELF_ROLE_CHANGE", path);
    }

    @Test
    @Tag("AC-5.3")
    @DisplayName("D6 change order: an implicit admin targeting themselves gets MEMBER_NOT_FOUND")
    void memberNotFoundWinsOverSelfRoleChange() throws Exception {
        // Given
        String path = memberPath(projects.acme().ben());

        // When / Then
        assertProblem(calls.as("ben", patch(path), roleBody("READER")), 404, "MEMBER_NOT_FOUND", path);
    }

    @ParameterizedTest(name = "D6 remove: {0} without access removes themselves: 403")
    @ValueSource(strings = {"ivy", "eve"})
    @Tag("AC-5.6")
    void aCallerWithoutAccessCannotLeave(String actor) throws Exception {
        // Given
        String path = memberPath(user(actor));

        // When / Then
        assertProblem(calls.as(actor, delete(path)), 403, "FORBIDDEN", path);
    }

    @ParameterizedTest(name = "AC-5.1 body {0}: {1}")
    @CsvSource(delimiter = '|', value = {"{}  | VALIDATION_FAILED", "{\"role\":\"OWNER\"} | MALFORMED_REQUEST",
            "{\"role\":2} | MALFORMED_REQUEST"})
    @Tag("AC-5.1")
    void anInvalidRoleChangeIsRejected(String body, String code) throws Exception {
        // Given
        String path = memberPath(projects.hal());

        // When / Then
        assertProblem(calls.as("ana", patch(path), body), 400, code, path);
        assertThat(roleOf(projects.hal())).contains(ProjectRole.TESTER);
    }

    private void archiveCheckoutAsCai() throws Exception {
        MvcResult result = calls.as("cai", post(checkout + "/archive"));
        assertThat(result.getResponse().getStatus()).as("Cai archives Checkout").isEqualTo(200);
    }

    private String memberPath(User user) {
        return member(projects.acme().orgId(), projects.checkout(), user.getId());
    }

    private static String roleBody(String role) {
        return "{\"role\":\"" + role + "\"}";
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

    private Optional<ProjectRole> roleOf(User user) {
        return projectMembershipRepository.findByUserIdAndProjectId(user.getId(), projects.checkout())
                .map(ProjectMembership::getRole);
    }

    private List<String> memberRoles() throws Exception {
        String json = calls.as("ana", get(checkout + "/members")).getResponse().getContentAsString();
        List<Map<String, Object>> members = JsonPath.read(json, "$");
        return members.stream().map(member -> member.get("email") + ":" + member.get("role")).toList();
    }
}
