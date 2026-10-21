package com.example.authservice.qa;

import com.example.authservice.enums.UserStatus;
import com.example.authservice.model.User;
import com.example.authservice.repository.OrgMembershipRepository;
import com.example.authservice.repository.ProjectMembershipRepository;
import com.example.authservice.repository.UserRepository;
import com.example.authservice.support.AcmeFixture;
import com.example.authservice.support.AuthServiceIntegrationTest;
import com.example.authservice.support.ProjectFixture;
import com.example.authservice.support.ProjectFixture.Projects;
import com.example.authservice.support.TestJwt;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.Map;

import static com.example.authservice.qa.QaSupport.as;
import static com.example.authservice.qa.QaSupport.signedRaw;
import static com.example.authservice.support.ApiCalls.bearer;
import static com.example.authservice.support.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * qa adversarial checks for US-7 and contract C5 ({@code GET /internal/projects/{projectId}/access}): forged,
 * expired, altered and other users' tokens, malformed and unknown ids, other methods and neighbouring paths, and
 * what the answers reveal. AC-7.6 (never routed by the gateway) is a manual check through the gateway; see the
 * report.
 */
@AuthServiceIntegrationTest
@Import(ProjectFixture.class)
class QaProjectAccessCheckTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectFixture fixture;

    @Autowired
    private AcmeFixture acmeFixture;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrgMembershipRepository orgMembershipRepository;

    @Autowired
    private ProjectMembershipRepository projectMembershipRepository;

    private Projects projects;
    private String checkoutAccess;

    @BeforeEach
    void seed() {
        projects = fixture.seed();
        checkoutAccess = access(projects.checkout().toString());
    }

    @ParameterizedTest(name = "qa AC-7.3: {0} is 401")
    @ValueSource(strings = {"EMPTY_BEARER", "GARBAGE", "BASIC", "ALG_NONE", "HS256", "OTHER_SECRET", "EXPIRED",
            "ALTERED_SUBJECT", "SUBJECT_NOT_UUID", "NO_SUBJECT", "UNKNOWN_SUBJECT", "NO_EXP", "TWO_TOKENS",
            "TOKEN_IN_QUERY", "TOKEN_IN_COOKIE"})
    @Tag("AC-7.3")
    @DisplayName("qa AC-7.3: every unusable credential is a 401 Problem with the Bearer challenge and reveals nothing")
    void everyUnusableCredentialIs401(String variant) throws Exception {
        // Given
        User hal = projects.hal();
        String valid = TestJwt.valid(hal.getId(), hal.getEmail());
        MockHttpServletRequestBuilder request = get(checkoutAccess);
        long now = Instant.now().getEpochSecond();
        switch (variant) {
            case "EMPTY_BEARER" -> request.header(HttpHeaders.AUTHORIZATION, "Bearer ");
            case "GARBAGE" -> request.header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt");
            case "BASIC" -> request.header(HttpHeaders.AUTHORIZATION, "Basic aGFsQGV4YW1wbGUuY29tOlNlY3JldDEyMyE=");
            case "ALG_NONE" -> request.header(HttpHeaders.AUTHORIZATION, bearer(TestJwt.unsigned(hal.getId(),
                    hal.getEmail())));
            case "HS256" -> request.header(HttpHeaders.AUTHORIZATION, bearer(TestJwt.signedWithHs256(hal.getId(),
                    hal.getEmail())));
            case "OTHER_SECRET" -> request.header(HttpHeaders.AUTHORIZATION,
                    bearer(TestJwt.signedWithAnotherSecret(hal.getId(), hal.getEmail())));
            case "EXPIRED" -> request.header(HttpHeaders.AUTHORIZATION, bearer(TestJwt.expired(hal.getId(),
                    hal.getEmail())));
            case "ALTERED_SUBJECT" -> request.header(HttpHeaders.AUTHORIZATION, bearer(TestJwt.withAlteredSubject(
                    projects.ivy().getId(), "ivy@example.com", hal.getId())));
            case "SUBJECT_NOT_UUID" -> request.header(HttpHeaders.AUTHORIZATION,
                    bearer(TestJwt.withSubject("hal@example.com", hal.getEmail())));
            case "NO_SUBJECT" -> request.header(HttpHeaders.AUTHORIZATION, bearer(TestJwt.withoutSubject(
                    hal.getEmail())));
            case "UNKNOWN_SUBJECT" -> request.header(HttpHeaders.AUTHORIZATION, bearer(TestJwt.valid(
                    AcmeFixture.UNKNOWN_ID, "nobody@example.com")));
            case "NO_EXP" -> request.header(HttpHeaders.AUTHORIZATION, bearer(signedRaw("{\"alg\":\"HS512\"}",
                    "{\"sub\":\"" + hal.getId() + "\",\"email\":\"hal@example.com\",\"iat\":" + now + "}")));
            case "TWO_TOKENS" -> request.header(HttpHeaders.AUTHORIZATION, bearer(valid) + " " + valid);
            case "TOKEN_IN_QUERY" -> request.param("access_token", valid);
            case "TOKEN_IN_COOKIE" -> request.header(HttpHeaders.COOKIE, "access_token=" + valid + "; token=" + valid);
            default -> throw new IllegalArgumentException(variant);
        }

        // When
        MvcResult result = mockMvc.perform(request).andReturn();

        // Then
        assertProblem(result, 401, "UNAUTHENTICATED", checkoutAccess);
        assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(projects.acme().orgId().toString(), "TESTER", "archived", "hal@example.com");
    }

    @ParameterizedTest(name = "qa AC-3.4 phase 1 / AC-7.3: a {0} user's earlier token is 401")
    @ValueSource(strings = {"SUSPENDED", "DELETED"})
    @Tag("AC-7.3")
    @DisplayName("qa AC-7.3: a suspended or deleted user's earlier token is 401 at the access check")
    void aSuspendedOrDeletedUsersTokenIs401(String state) throws Exception {
        // Given
        User hal = projects.hal();
        String token = TestJwt.valid(hal.getId(), hal.getEmail());
        if (state.equals("SUSPENDED")) {
            acmeFixture.setStatus(hal, UserStatus.SUSPENDED);
        } else {
            projectMembershipRepository.delete(projectMembershipRepository
                    .findByUserIdAndProjectId(hal.getId(), projects.checkout()).orElseThrow());
            orgMembershipRepository.deleteAll(orgMembershipRepository.findAll().stream()
                    .filter(membership -> membership.getUser().getId().equals(hal.getId())).toList());
            userRepository.deleteById(hal.getId());
        }

        // When
        MvcResult result = mockMvc.perform(get(checkoutAccess).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andReturn();

        // Then
        assertProblem(result, 401, "UNAUTHENTICATED", checkoutAccess);
    }

    @ParameterizedTest(name = "qa AC-7.2: id \"{0}\" is 400 MALFORMED_REQUEST")
    @ValueSource(strings = {"not-a-uuid", "123", "3b9d5c1e-0000-4000-8000-0000000000fff",
            "zzzzzzzz-zzzz-zzzz-zzzz-zzzzzzzzzzzz", "3b9d5c1e00004000800000000000000ff",
            "3b9d5c1e-0000-4000-8000-0000000000ff-1", "3b9d5c1e-0000-4000-8000"})
    @Tag("AC-7.2")
    @DisplayName("qa AC-7.2: malformed project ids are 400 MALFORMED_REQUEST")
    void malformedIdsAre400(String id) throws Exception {
        // When
        MvcResult result = mockMvc.perform(as(get("/internal/projects/" + id + "/access"), projects.acme().ana()))
                .andReturn();

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(JsonPath.<String>read(result.getResponse().getContentAsString(), "$.code"))
                .isEqualTo("MALFORMED_REQUEST");
    }

    @ParameterizedTest(name = "qa AC-7.2: id {0} is 403, like any project without access")
    @ValueSource(strings = {"00000000-0000-0000-0000-000000000000", "ffffffff-ffff-ffff-ffff-ffffffffffff",
            "ORG_ID", "USER_ID", "MEMBERSHIP_ID"})
    @Tag("AC-7.2")
    @DisplayName("qa AC-7.2: ids of other kinds of rows, and the nil UUID, are the same 403 as an unknown project")
    void idsOfOtherRowsAreForbidden(String id) throws Exception {
        // Given
        String projectId = switch (id) {
            case "ORG_ID" -> projects.acme().orgId().toString();
            case "USER_ID" -> projects.acme().ana().getId().toString();
            case "MEMBERSHIP_ID" -> projectMembershipRepository
                    .findByUserIdAndProjectId(projects.hal().getId(), projects.checkout()).orElseThrow().getId()
                    .toString();
            default -> id;
        };
        String path = access(projectId);
        String unknownDetail = JsonPath.read(mockMvc.perform(as(get(access(AcmeFixture.UNKNOWN_ID.toString())),
                projects.acme().ana())).andReturn().getResponse().getContentAsString(), "$.detail");

        // When
        MvcResult result = mockMvc.perform(as(get(path), projects.acme().ana())).andReturn();

        // Then
        assertProblem(result, 403, "FORBIDDEN", path);
        assertThat(JsonPath.<String>read(result.getResponse().getContentAsString(), "$.detail"))
                .isEqualTo(unknownDetail);
    }

    @Test
    @Tag("AC-7.2")
    @DisplayName("qa AC-7.2: upper-case hex in the id is the same project; the answer uses the canonical id")
    void upperCaseIdsAreTheSameProject() throws Exception {
        // When
        MvcResult result = mockMvc.perform(as(get(access(projects.checkout().toString().toUpperCase())),
                projects.hal())).andReturn();

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonPath.<String>read(result.getResponse().getContentAsString(), "$.projectId"))
                .isEqualTo(projects.checkout().toString());
    }

    @ParameterizedTest(name = "qa AC-7.1: {0}'s token for Checkout is 403 and identical to an unknown project")
    @ValueSource(strings = {"ivy", "eve"})
    @Tag("AC-7.1")
    @Tag("AC-7.2")
    @DisplayName("qa C5: no access and an unknown project are indistinguishable, apart from the path")
    void noAccessAndUnknownAreIndistinguishable(String user) throws Exception {
        // Given
        User caller = user.equals("ivy") ? projects.ivy() : projects.acme().eve();

        // When
        MvcResult noAccess = mockMvc.perform(as(get(checkoutAccess), caller)).andReturn();
        MvcResult unknown = mockMvc.perform(as(get(access(AcmeFixture.UNKNOWN_ID.toString())), caller)).andReturn();

        // Then
        assertProblem(noAccess, 403, "FORBIDDEN", checkoutAccess);
        assertThat(noAccess.getResponse().getContentAsString().replace(projects.checkout().toString(), "ID"))
                .isEqualTo(unknown.getResponse().getContentAsString().replace(AcmeFixture.UNKNOWN_ID.toString(), "ID"));
        assertThat(noAccess.getResponse().getContentAsString())
                .doesNotContain(projects.acme().orgId().toString(), "Checkout", "archived");
    }

    @Test
    @Tag("AC-7.1")
    @DisplayName("qa C5: a 200 has exactly the four C5 fields, JSON, no cookie, and nothing about other users")
    void a200HasExactlyTheC5Fields() throws Exception {
        // When
        MvcResult result = mockMvc.perform(as(get(checkoutAccess), projects.gus())).andReturn();

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentType()).isEqualTo("application/json");
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
        Map<String, Object> body = JsonPath.read(result.getResponse().getContentAsString(), "$");
        assertThat(body).isEqualTo(Map.of("projectId", projects.checkout().toString(),
                "orgId", projects.acme().orgId().toString(), "role", "MEMBER", "archived", false));
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("Checkout", "@example.com", "Acme", "userId", "email");
    }

    @ParameterizedTest(name = "qa C5: {0} on the access check is 405 and changes nothing")
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    @Tag("AC-7.1")
    @DisplayName("qa C5: only GET exists on the access check")
    void onlyGetExists(String method) throws Exception {
        // Given
        MockHttpServletRequestBuilder request = switch (method) {
            case "POST" -> post(checkoutAccess);
            case "PUT" -> put(checkoutAccess);
            case "PATCH" -> patch(checkoutAccess);
            default -> delete(checkoutAccess);
        };

        // When
        MvcResult result = mockMvc.perform(as(request.contentType("application/json")
                .content("{\"role\":\"ADMIN\"}"), projects.ivy())).andReturn();

        // Then
        assertProblem(result, 405, "METHOD_NOT_ALLOWED", checkoutAccess);
        assertThat(projectMembershipRepository.findByUserIdAndProjectId(projects.ivy().getId(), projects.checkout()))
                .isEmpty();
    }

    @ParameterizedTest(name = "qa C5: GET {0} is 404")
    @ValueSource(strings = {"/internal/projects", "/internal/projects/CHECKOUT", "/internal/projects/CHECKOUT/access/x",
            "/internal/projects/CHECKOUT/members", "/internal", "/internal/orgs/ACME/access"})
    @Tag("AC-7.1")
    @DisplayName("qa C5: neighbouring /internal paths don't exist and reveal nothing")
    void neighbouringInternalPathsDontExist(String raw) throws Exception {
        // Given
        String path = raw.replace("CHECKOUT", projects.checkout().toString())
                .replace("ACME", projects.acme().orgId().toString());

        // When
        MvcResult result = mockMvc.perform(as(get(path), projects.acme().ana())).andReturn();

        // Then
        assertProblem(result, 404, "NOT_FOUND", path);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("Checkout", "@example.com");
    }

    @Test
    @Tag("AC-7.3")
    @DisplayName("qa C5: a neighbouring /internal path without a token is 401, not 404")
    void neighbouringInternalPathsNeedAToken() throws Exception {
        // When
        MvcResult result = mockMvc.perform(get("/internal/projects")).andReturn();

        // Then
        assertProblem(result, 401, "UNAUTHENTICATED", "/internal/projects");
    }

    @Test
    @Tag("AC-7.5")
    @DisplayName("qa AC-7.5: a demotion and a promotion are both visible on the next access check")
    void roleChangesAreVisibleOnTheNextRequest() throws Exception {
        // Given
        String token = TestJwt.valid(projects.gus().getId(), projects.gus().getEmail());
        String gusMember = "/api/orgs/" + projects.acme().orgId() + "/projects/" + projects.checkout() + "/members/"
                + projects.gus().getId();

        // When / Then
        assertThat(role(token)).isEqualTo("MEMBER");
        mockMvc.perform(as(patch(gusMember).contentType("application/json").content("{\"role\":\"TESTER\"}"),
                projects.acme().cai()));
        assertThat(role(token)).isEqualTo("TESTER");
        mockMvc.perform(as(patch("/api/orgs/" + projects.acme().orgId() + "/members/" + projects.gus().getId())
                .contentType("application/json").content("{\"role\":\"ADMIN\"}"), projects.acme().ana()));
        assertThat(role(token)).isEqualTo("ADMIN");
        mockMvc.perform(as(patch("/api/orgs/" + projects.acme().orgId() + "/members/" + projects.gus().getId())
                .contentType("application/json").content("{\"role\":\"READER\"}"), projects.acme().ana()));
        assertThat(role(token)).isEqualTo("READER");
        mockMvc.perform(as(delete("/api/orgs/" + projects.acme().orgId() + "/members/" + projects.gus().getId()),
                projects.acme().ana()));
        assertProblem(mockMvc.perform(get(checkoutAccess).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andReturn(), 403, "FORBIDDEN", checkoutAccess);
    }

    private String role(String token) throws Exception {
        MvcResult result = mockMvc.perform(get(checkoutAccess).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return JsonPath.read(result.getResponse().getContentAsString(), "$.role");
    }

    private static String access(String projectId) {
        return "/internal/projects/" + projectId + "/access";
    }
}
