package com.example.authservice.controller;

import com.example.authservice.enums.ProjectRole;
import com.example.authservice.model.Organization;
import com.example.authservice.model.ProjectMembership;
import com.example.authservice.model.User;
import com.example.authservice.repository.OrganizationRepository;
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
import org.junit.jupiter.api.RepeatedTest;
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
import java.util.UUID;
import java.util.concurrent.Callable;

import static com.example.authservice.support.ProblemAssertions.assertProblem;
import static com.example.authservice.support.ProjectCalls.members;
import static com.example.authservice.support.ProjectCalls.project;
import static com.example.authservice.support.ProjectCalls.projects;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * US-4 (T13): list and add project members, AC-3.6 row 2, and the member-list parts of AC-1.1 and AC-3.5. The add
 * error order is D6's: 400, 403, 409 {@code PROJECT_ARCHIVED}, 422 {@code NOT_ORG_MEMBER}, 409
 * {@code ALREADY_MEMBER}, 422 {@code PROJECT_ROLE_NOT_ALLOWED}.
 */
@AuthServiceIntegrationTest
@Import(ProjectFixture.class)
class ProjectMembersTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectFixture fixture;

    @Autowired
    private ProjectMembershipRepository projectMembershipRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    private Projects projects;
    private ProjectCalls calls;
    private String checkoutMembers;

    @BeforeEach
    void seedProjects() {
        projects = fixture.seed();
        calls = new ProjectCalls(mockMvc);
        checkoutMembers = members(projects.acme().orgId(), projects.checkout());
    }

    @Test
    @Tag("AC-4.1")
    @DisplayName("AC-4.1 Ana sees the explicit members ordered by email, with exactly the member fields")
    void theExplicitMembersAreListedByEmail() throws Exception {
        // When
        MvcResult result = calls.as("ana", get(checkoutMembers));

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String json = result.getResponse().getContentAsString();
        assertThat(memberRoles(json)).containsExactly(
                "cai@example.com:ADMIN", "dan@example.com:READER", "gus@example.com:MEMBER", "hal@example.com:TESTER");
        List<Map<String, Object>> members = JsonPath.read(json, "$");
        assertThat(members).allSatisfy(member -> assertThat(member)
                .containsOnlyKeys("userId", "email", "fullName", "role", "joinedAt"));
        assertThat(json.toLowerCase()).doesNotContain("password");
        assertThat(JsonPath.<String>read(json, "$[0].userId")).isEqualTo(projects.acme().cai().getId().toString());
        assertThat(JsonPath.<String>read(json, "$[0].fullName")).isEqualTo(projects.acme().cai().getFullName());
        assertThat(JsonPath.<String>read(json, "$[0].joinedAt")).isNotBlank();
    }

    @ParameterizedTest(name = "AC-4.1 {0}: {1}")
    @CsvSource({"hal, 200", "dan, 200", "ivy, 403", "eve, 403"})
    @Tag("AC-4.1")
    void everyoneWithAccessSeesTheMembersNobodyElseDoes(String actor, int status) throws Exception {
        // When
        MvcResult result = calls.as(actor, get(checkoutMembers));

        // Then
        if (status == 200) {
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            assertThat(memberRoles(result.getResponse().getContentAsString())).hasSize(4);
            return;
        }
        assertProblem(result, 403, "FORBIDDEN", checkoutMembers);
    }

    @Test
    @Tag("AC-1.1")
    @DisplayName("AC-1.1 A new project has no explicit members: its creator's member list is []")
    void aNewProjectHasNoExplicitMembers() throws Exception {
        // Given
        MvcResult created = calls.as("ben", post(projects(projects.acme().orgId())), "{\"name\":\"Mobile App\"}");
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        // When
        MvcResult result = calls.as("ben", get(members(projects.acme().orgId(), id)));

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString()).isEqualTo("[]");
    }

    @Test
    @Tag("AC-3.5")
    @DisplayName("AC-3.5 The member list of an archived project stays readable")
    void theMemberListOfAnArchivedProjectStaysReadable() throws Exception {
        // Given
        archiveCheckoutAsCai();

        // When
        MvcResult result = calls.as("dan", get(checkoutMembers));

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @Tag("AC-4.2")
    @DisplayName("AC-4.2 A project admin adds an organization member, who gets access at once")
    void aProjectAdminAddsAnOrganizationMember() throws Exception {
        // Given
        String ivyToken = calls.tokenOf("ivy");

        // When
        MvcResult result = calls.as("cai", post(checkoutMembers), addBody(projects.ivy().getId(), "TESTER"));

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        DocumentContext body = JsonPath.parse(result.getResponse().getContentAsString());
        assertThat(body.read("$.userId", String.class)).isEqualTo(projects.ivy().getId().toString());
        assertThat(body.read("$.email", String.class)).isEqualTo("ivy@example.com");
        assertThat(body.read("$.fullName", String.class)).isEqualTo("Ivy Moss");
        assertThat(body.read("$.role", String.class)).isEqualTo("TESTER");
        assertThat(body.read("$.joinedAt", String.class)).isNotBlank();
        MvcResult view = calls.withToken(ivyToken, get(project(projects.acme().orgId(), projects.checkout())));
        assertThat(view.getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonPath.<String>read(view.getResponse().getContentAsString(), "$.myRole")).isEqualTo("TESTER");
    }

    @ParameterizedTest(name = "AC-4.3 {0}: {1}")
    @CsvSource({"ana, 201", "ben, 201", "cai, 201", "gus, 403", "hal, 403", "dan, 403", "ivy, 403", "eve, 403"})
    @Tag("AC-4.3")
    void onlyEffectiveProjectAdminsCanAddMembers(String actor, int status) throws Exception {
        // When
        MvcResult result = calls.as(actor, post(checkoutMembers), addBody(projects.ivy().getId(), "READER"));

        // Then
        if (status == 201) {
            assertThat(result.getResponse().getStatus()).isEqualTo(201);
            assertThat(roleIn(projects.checkout(), projects.ivy())).contains(ProjectRole.READER);
            return;
        }
        assertProblem(result, 403, "FORBIDDEN", checkoutMembers);
        assertThat(roleIn(projects.checkout(), projects.ivy())).isEmpty();
    }

    @ParameterizedTest(name = "AC-4.4 user {0}")
    @ValueSource(strings = {"EVE", "UNKNOWN"})
    @Tag("AC-4.4")
    void onlyMembersOfTheProjectsOrganizationCanBeAdded(String user) throws Exception {
        // Given
        UUID userId = user.equals("EVE") ? projects.acme().eve().getId() : AcmeFixture.UNKNOWN_ID;

        // When
        MvcResult result = calls.as("ana", post(checkoutMembers), addBody(userId, "READER"));

        // Then
        assertProblem(result, 422, "NOT_ORG_MEMBER", checkoutMembers);
        assertThat(checkoutMembershipCount()).isEqualTo(4);
    }

    @Test
    @Tag("AC-4.5")
    @DisplayName("AC-4.5 Adding someone who is already an explicit member is a conflict")
    void addingAnExistingMemberIsAConflict() throws Exception {
        // When
        MvcResult result = calls.as("ana", post(checkoutMembers), addBody(projects.gus().getId(), "ADMIN"));

        // Then
        assertProblem(result, 409, "ALREADY_MEMBER", checkoutMembers);
        assertThat(roleIn(projects.checkout(), projects.gus())).contains(ProjectRole.MEMBER);
    }

    @Test
    @Tag("AC-4.5")
    @DisplayName("D6 add order: ALREADY_MEMBER wins over PROJECT_ROLE_NOT_ALLOWED (Dan, organization READER, as ADMIN)")
    void alreadyMemberWinsOverRoleNotAllowed() throws Exception {
        // When
        MvcResult result = calls.as("ana", post(checkoutMembers), addBody(projects.acme().dan().getId(), "ADMIN"));

        // Then
        assertProblem(result, 409, "ALREADY_MEMBER", checkoutMembers);
    }

    @ParameterizedTest(name = "AC-4.6 Dan as {0}")
    @ValueSource(strings = {"READER", "TESTER", "MEMBER", "ADMIN"})
    @Tag("AC-4.6")
    void anOrganizationReaderCanOnlyBeAProjectReader(String role) throws Exception {
        // Given
        String billingMembers = members(projects.acme().orgId(), projects.billing());

        // When
        MvcResult result = calls.as("ana", post(billingMembers), addBody(projects.acme().dan().getId(), role));

        // Then
        String danView = project(projects.acme().orgId(), projects.billing());
        if (role.equals("READER")) {
            assertThat(result.getResponse().getStatus()).isEqualTo(201);
            MvcResult view = calls.as("dan", get(danView));
            assertThat(JsonPath.<String>read(view.getResponse().getContentAsString(), "$.myRole")).isEqualTo("READER");
            return;
        }
        assertProblem(result, 422, "PROJECT_ROLE_NOT_ALLOWED", billingMembers);
        assertProblem(calls.as("dan", get(danView)), 403, "FORBIDDEN", danView);
    }

    @ParameterizedTest(name = "AC-4.7 body {0}: {1}")
    @CsvSource(delimiter = '|', value = {
            "{}                                       | VALIDATION_FAILED",
            "{\"userId\":\"IVY\"}                     | VALIDATION_FAILED",
            "{\"userId\":\"IVY\",\"role\":\"OWNER\"}  | MALFORMED_REQUEST",
            "{\"userId\":\"IVY\",\"role\":1}          | MALFORMED_REQUEST",
            "{\"userId\":\"not-a-uuid\",\"role\":\"READER\"} | MALFORMED_REQUEST"})
    @Tag("AC-4.7")
    void anInvalidAddRequestIsRejected(String body, String code) throws Exception {
        // When
        MvcResult result = calls.as("ana", post(checkoutMembers),
                body.replace("IVY", projects.ivy().getId().toString()));

        // Then
        DocumentContext problem = assertProblem(result, 400, code, checkoutMembers);
        if (code.equals("VALIDATION_FAILED")) {
            assertThat(problem.read("$.errors[*].field", List.class)).contains("role");
        }
        assertThat(checkoutMembershipCount()).isEqualTo(4);
    }

    @Test
    @Tag("AC-4.8")
    @DisplayName("AC-4.8 A project membership uses no organization seat")
    void aProjectMembershipUsesNoSeat() throws Exception {
        // Given Acme's seat limit is 7, so its 7 members use every seat
        Organization acme = organizationRepository.findById(projects.acme().orgId()).orElseThrow();
        acme.setSeatLimit(7);
        organizationRepository.save(acme);
        String billingMembers = members(projects.acme().orgId(), projects.billing());

        // When
        MvcResult result = calls.as("ana", post(billingMembers), addBody(projects.ivy().getId(), "MEMBER"));

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        String invitations = "/api/orgs/" + projects.acme().orgId() + "/invitations";
        assertProblem(calls.as("ben", post(invitations), "{\"email\":\"fay@example.com\",\"role\":\"MEMBER\"}"),
                409, "SEAT_LIMIT_REACHED", invitations);
    }

    @Test
    @Tag("AC-4.9")
    @DisplayName("AC-4.9 An implicit admin can also be added explicitly, and keeps admin rights")
    void anImplicitAdminCanBeAddedExplicitly() throws Exception {
        // When
        MvcResult result = calls.as("ana", post(checkoutMembers), addBody(projects.acme().ben().getId(), "READER"));

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        MvcResult view = calls.as("ben", get(project(projects.acme().orgId(), projects.checkout())));
        assertThat(JsonPath.<String>read(view.getResponse().getContentAsString(), "$.myRole")).isEqualTo("ADMIN");
    }

    @Test
    @Tag("AC-3.6")
    @DisplayName("AC-3.6 row 2: adding a member to an archived project is PROJECT_ARCHIVED")
    void addingToAnArchivedProjectIsRejected() throws Exception {
        // Given
        archiveCheckoutAsCai();

        // When
        MvcResult result = calls.as("cai", post(checkoutMembers), addBody(projects.ivy().getId(), "READER"));

        // Then
        assertProblem(result, 409, "PROJECT_ARCHIVED", checkoutMembers);
        assertThat(checkoutMembershipCount()).isEqualTo(4);
    }

    @Test
    @Tag("AC-3.6")
    @DisplayName("D6 add order: PROJECT_ARCHIVED wins over NOT_ORG_MEMBER")
    void archivedWinsOverNotOrgMember() throws Exception {
        // Given
        archiveCheckoutAsCai();

        // When
        MvcResult result = calls.as("cai", post(checkoutMembers), addBody(projects.acme().eve().getId(), "READER"));

        // Then
        assertProblem(result, 409, "PROJECT_ARCHIVED", checkoutMembers);
    }

    @RepeatedTest(value = 5, name = "NFR Two simultaneous adds of one user give one membership and one ALREADY_MEMBER (run {currentRepetition})")
    @Tag("AC-4.5")
    void twoSimultaneousAddsOfOneUserGiveOneMembership() throws Exception {
        // Given
        calls.login("ana", "ben");
        String body = addBody(projects.ivy().getId(), "READER");
        List<Callable<MvcResult>> adds = List.of(
                () -> calls.as("ana", post(checkoutMembers), body),
                () -> calls.as("ben", post(checkoutMembers), body));

        // When
        List<MvcResult> results = ProjectCalls.simultaneously(adds);

        // Then
        assertThat(results).filteredOn(result -> result.getResponse().getStatus() == 201).hasSize(1);
        MvcResult loser = results.stream().filter(result -> result.getResponse().getStatus() != 201).findFirst()
                .orElseThrow();
        assertProblem(loser, 409, "ALREADY_MEMBER", checkoutMembers);
        assertThat(checkoutMembershipCount()).isEqualTo(5);
    }

    private void archiveCheckoutAsCai() throws Exception {
        MvcResult result = calls.as("cai", post(project(projects.acme().orgId(), projects.checkout()) + "/archive"));
        assertThat(result.getResponse().getStatus()).as("Cai archives Checkout").isEqualTo(200);
    }

    private static String addBody(UUID userId, String role) {
        return "{\"userId\":\"" + userId + "\",\"role\":\"" + role + "\"}";
    }

    private static List<String> memberRoles(String json) {
        List<Map<String, Object>> members = JsonPath.read(json, "$");
        return members.stream().map(member -> member.get("email") + ":" + member.get("role")).toList();
    }

    private Optional<ProjectRole> roleIn(UUID projectId, User user) {
        return projectMembershipRepository.findByUserIdAndProjectId(user.getId(), projectId)
                .map(ProjectMembership::getRole);
    }

    private long checkoutMembershipCount() {
        return projectMembershipRepository.findAll().stream()
                .filter(membership -> membership.getProject().getId().equals(projects.checkout()))
                .count();
    }
}
