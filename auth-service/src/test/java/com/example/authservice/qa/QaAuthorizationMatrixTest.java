package com.example.authservice.qa;

import com.example.authservice.enums.InvitationStatus;
import com.example.authservice.enums.OrganizationRole;
import com.example.authservice.model.Invitation;
import com.example.authservice.model.OrgMembership;
import com.example.authservice.model.Organization;
import com.example.authservice.model.User;
import com.example.authservice.repository.InvitationRepository;
import com.example.authservice.repository.OrgMembershipRepository;
import com.example.authservice.repository.OrganizationRepository;
import com.example.authservice.support.AcmeFixture;
import com.example.authservice.support.AcmeFixture.Acme;
import com.example.authservice.support.AuthServiceIntegrationTest;
import com.example.authservice.support.TestJwt;
import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static com.example.authservice.qa.QaSupport.as;
import static com.example.authservice.qa.QaSupport.json;
import static com.example.authservice.qa.QaSupport.seedPendingInvitation;
import static com.example.authservice.support.ApiCalls.bearer;
import static com.example.authservice.support.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * qa: the full role x operation matrix of D3/D11, wider than the spec's example rows, plus cross-organization access
 * and IDOR on orgId, userId and invitationId. Tokens are minted with the shared test secret (see QaSupport#tokenFor).
 */
@AuthServiceIntegrationTest
class QaAuthorizationMatrixTest {

    private static final List<String> ACTORS = List.of("ana", "ben", "cai", "dan", "eve");
    private static final List<String> MEMBERS = List.of("ana", "ben", "cai", "dan");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AcmeFixture fixture;

    @Autowired
    private OrgMembershipRepository orgMembershipRepository;

    @Autowired
    private InvitationRepository invitationRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    private Acme acme;

    @BeforeEach
    void seedAcme() {
        acme = fixture.seed();
    }

    @ParameterizedTest(name = "AC-5.2 (qa full matrix) {0} invites with role {1}: {2} {3}")
    @CsvSource({
            "ana, OWNER,  201, ''", "ana, ADMIN,  201, ''", "ana, MEMBER, 201, ''", "ana, READER, 201, ''",
            "ben, OWNER,  422, ROLE_CEILING", "ben, ADMIN, 201, ''", "ben, MEMBER, 201, ''", "ben, READER, 201, ''",
            "cai, OWNER,  403, FORBIDDEN", "cai, ADMIN, 403, FORBIDDEN", "cai, MEMBER, 403, FORBIDDEN",
            "cai, READER, 403, FORBIDDEN",
            "dan, OWNER,  403, FORBIDDEN", "dan, ADMIN, 403, FORBIDDEN", "dan, MEMBER, 403, FORBIDDEN",
            "dan, READER, 403, FORBIDDEN",
            "eve, OWNER,  403, FORBIDDEN", "eve, ADMIN, 403, FORBIDDEN", "eve, MEMBER, 403, FORBIDDEN",
            "eve, READER, 403, FORBIDDEN"
    })
    @Tag("AC-5.2")
    void everyRoleInvitesWithEveryRole(String actor, OrganizationRole role, int status, String code) throws Exception {
        // When
        String path = invitations(acme.orgId());
        MvcResult result = mockMvc.perform(json(as(post(path), user(actor)),
                "{\"email\":\"fay@example.com\",\"role\":\"" + role + "\"}")).andReturn();

        // Then
        if (status == 201) {
            assertThat(result.getResponse().getStatus()).isEqualTo(201);
            assertThat(invitationRepository.findAll()).singleElement()
                    .satisfies(invitation -> assertThat(invitation.getRole()).isEqualTo(role.name()));
        } else {
            assertProblem(result, status, code, path);
            assertThat(invitationRepository.findAll()).isEmpty();
        }
    }

    @ParameterizedTest(name = "AC-5.10 (qa all roles) {0} revokes Ben's invitation: {1}")
    @CsvSource({"ana, 204", "ben, 204", "cai, 403", "dan, 403", "eve, 403"})
    @Tag("AC-5.10")
    void everyRoleRevokes(String actor, int status) throws Exception {
        // Given
        Invitation invitation = seedPendingInvitation(invitationRepository, acmeOrg(), acme.ben(), "fay@example.com",
                OrganizationRole.MEMBER, "inv-matrix-QA");
        String path = invitations(acme.orgId()) + "/" + invitation.getId();

        // When
        MvcResult result = mockMvc.perform(as(delete(path), user(actor))).andReturn();

        // Then
        InvitationStatus after = invitationRepository.findById(invitation.getId()).orElseThrow().getStatus();
        if (status == 204) {
            assertThat(result.getResponse().getStatus()).isEqualTo(204);
            assertThat(after).isEqualTo(InvitationStatus.REVOKED);
        } else {
            assertProblem(result, 403, "FORBIDDEN", path);
            assertThat(after).isEqualTo(InvitationStatus.PENDING);
        }
    }

    @ParameterizedTest(name = "AC-7.1 (qa all roles) {0} lists the members: {1}")
    @CsvSource({"ana, 200", "ben, 200", "cai, 200", "dan, 200", "eve, 403"})
    @Tag("AC-7.1")
    void everyRoleListsMembers(String actor, int status) throws Exception {
        // When
        String path = "/api/orgs/" + acme.orgId() + "/members";
        MvcResult result = mockMvc.perform(as(get(path), user(actor))).andReturn();

        // Then
        if (status == 403) {
            assertProblem(result, 403, "FORBIDDEN", path);
        } else {
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            assertThat(JsonPath.<List<String>>read(result.getResponse().getContentAsString(), "$[*].email"))
                    .containsExactly("ana@example.com", "ben@example.com", "cai@example.com", "dan@example.com");
        }
    }

    /**
     * Expected outcome from D11 and the Q6 order, written from the spec, not from the code: a non-member is 403; a
     * member targeting themselves is SELF_ROLE_CHANGE; below ADMIN is 403; a target or new role above the actor's
     * rank is ROLE_CEILING; otherwise 200.
     */
    static Stream<Arguments> changeRoleMatrix() {
        List<Arguments> rows = new ArrayList<>();
        for (String actor : ACTORS) {
            for (String target : MEMBERS) {
                for (OrganizationRole newRole : OrganizationRole.values()) {
                    rows.add(Arguments.of(actor, target, newRole, expectedChange(actor, target, newRole)));
                }
            }
        }
        return rows.stream();
    }

    private static String expectedChange(String actor, String target, OrganizationRole newRole) {
        if (actor.equals("eve")) {
            return "403 FORBIDDEN";
        }
        if (actor.equals(target)) {
            return "422 SELF_ROLE_CHANGE";
        }
        OrganizationRole actorRole = acmeRole(actor);
        if (actorRole.getRank() < OrganizationRole.ADMIN.getRank()) {
            return "403 FORBIDDEN";
        }
        if (acmeRole(target).getRank() > actorRole.getRank() || newRole.getRank() > actorRole.getRank()) {
            return "422 ROLE_CEILING";
        }
        return "200";
    }

    @ParameterizedTest(name = "AC-7.2 (qa full matrix) {0} changes {1} to {2}: {3}")
    @MethodSource("changeRoleMatrix")
    @Tag("AC-7.2")
    void everyRoleChangesEveryMemberToEveryRole(String actor, String target, OrganizationRole newRole,
                                                String expected) throws Exception {
        // Given
        User targetUser = user(target);
        OrganizationRole before = roleInAcme(targetUser);
        String path = member(acme.orgId(), targetUser.getId());

        // When
        MvcResult result = mockMvc.perform(json(as(patch(path), user(actor)), "{\"role\":\"" + newRole + "\"}"))
                .andReturn();

        // Then
        if (expected.equals("200")) {
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            assertThat(roleInAcme(targetUser)).isEqualTo(newRole);
        } else {
            String[] parts = expected.split(" ");
            assertProblem(result, Integer.parseInt(parts[0]), parts[1], path);
            assertThat(roleInAcme(targetUser)).isEqualTo(before);
        }
        assertThat(orgMembershipRepository.countByOrganizationId(acme.orgId())).isEqualTo(4);
    }

    /**
     * From D11 and Q6: a non-member is 403; leaving is allowed except for the last owner (LAST_OWNER); removing someone
     * else needs ADMIN (403) and a target rank at most the actor's (ROLE_CEILING); a non-member target is
     * MEMBER_NOT_FOUND for admins and 403 for others.
     */
    static Stream<Arguments> removeMatrix() {
        List<Arguments> rows = new ArrayList<>();
        for (String actor : ACTORS) {
            for (String target : ACTORS) {
                rows.add(Arguments.of(actor, target, expectedRemove(actor, target)));
            }
        }
        return rows.stream();
    }

    private static String expectedRemove(String actor, String target) {
        if (actor.equals("eve")) {
            return "403 FORBIDDEN";
        }
        OrganizationRole actorRole = acmeRole(actor);
        if (actor.equals(target)) {
            return actorRole == OrganizationRole.OWNER ? "409 LAST_OWNER" : "204";
        }
        if (actorRole.getRank() < OrganizationRole.ADMIN.getRank()) {
            return "403 FORBIDDEN";
        }
        if (target.equals("eve")) {
            return "404 MEMBER_NOT_FOUND";
        }
        return acmeRole(target).getRank() > actorRole.getRank() ? "422 ROLE_CEILING" : "204";
    }

    @ParameterizedTest(name = "AC-8.1 (qa full matrix) {0} removes {1}: {2}")
    @MethodSource("removeMatrix")
    @Tag("AC-8.1")
    void everyRoleRemovesEveryone(String actor, String target, String expected) throws Exception {
        // Given
        User targetUser = user(target);
        OrganizationRole before = roleInAcme(targetUser);
        String path = member(acme.orgId(), targetUser.getId());

        // When
        MvcResult result = mockMvc.perform(as(delete(path), user(actor))).andReturn();

        // Then
        if (expected.equals("204")) {
            assertThat(result.getResponse().getStatus()).isEqualTo(204);
            assertThat(roleInAcme(targetUser)).isNull();
        } else {
            String[] parts = expected.split(" ");
            assertProblem(result, Integer.parseInt(parts[0]), parts[1], path);
            assertThat(roleInAcme(targetUser)).isEqualTo(before);
        }
    }

    static Stream<Arguments> orgOperations() {
        return Stream.of(
                Arguments.of("view organization", "GET", ""),
                Arguments.of("invite", "POST", "/invitations"),
                Arguments.of("list invitations", "GET", "/invitations"),
                Arguments.of("revoke invitation", "DELETE", "/invitations/{invitation}"),
                Arguments.of("list members", "GET", "/members"),
                Arguments.of("change role", "PATCH", "/members/{member}"),
                Arguments.of("remove member", "DELETE", "/members/{member}"));
    }

    @ParameterizedTest(name = "qa cross-org: the OWNER of Initech can't {0} in Acme")
    @MethodSource("orgOperations")
    @Tag("AC-4.5")
    void anOwnerOfAnotherOrganizationGetsForbiddenInAcme(String operation, String method, String suffix)
            throws Exception {
        // Given Eve owns Initech; Acme has a pending invitation I
        Organization initech = fixture.organization("Initech QA");
        fixture.member(initech, acme.eve(), OrganizationRole.OWNER);
        Invitation i = seedPendingInvitation(invitationRepository, acmeOrg(), acme.ben(), "fay@example.com",
                OrganizationRole.MEMBER, "inv-cross-QA");
        String path = "/api/orgs/" + acme.orgId()
                + suffix.replace("{invitation}", i.getId().toString()).replace("{member}", acme.cai().getId().toString());

        // When
        MvcResult result = mockMvc.perform(request(method, path, acme.eve())).andReturn();

        // Then
        assertProblem(result, 403, "FORBIDDEN", path);
        assertAcmeUnchanged(i);
    }

    @ParameterizedTest(name = "qa cross-org: the ADMIN of Acme can't {0} in Initech")
    @MethodSource("orgOperations")
    @Tag("AC-4.5")
    void anAdminOfAcmeGetsForbiddenInAnotherOrganization(String operation, String method, String suffix)
            throws Exception {
        // Given Initech (Eve OWNER, Gus MEMBER) with a pending invitation J
        Organization initech = fixture.organization("Initech QA");
        fixture.member(initech, acme.eve(), OrganizationRole.OWNER);
        User gus = fixture.user("gus@example.com", "Gus Ray");
        fixture.member(initech, gus, OrganizationRole.MEMBER);
        Invitation j = seedPendingInvitation(invitationRepository, initech, acme.eve(), "hal@example.com",
                OrganizationRole.MEMBER, "inv-initech-QA");
        String path = "/api/orgs/" + initech.getId()
                + suffix.replace("{invitation}", j.getId().toString()).replace("{member}", gus.getId().toString());

        // When
        MvcResult result = mockMvc.perform(request(method, path, acme.ben())).andReturn();

        // Then
        assertProblem(result, 403, "FORBIDDEN", path);
        assertThat(invitationRepository.findById(j.getId()).orElseThrow().getStatus())
                .isEqualTo(InvitationStatus.PENDING);
        assertThat(orgMembershipRepository.findByUserIdAndOrganizationId(gus.getId(), initech.getId()))
                .get().extracting(OrgMembership::getRole).isEqualTo(OrganizationRole.MEMBER);
        assertThat(invitationRepository.findAll()).hasSize(1);
    }

    @Test
    @Tag("AC-5.10")
    @DisplayName("qa IDOR invitationId: Initech's owner can't revoke Acme's invitation through Initech's path")
    void anInvitationOfAnotherOrganizationCantBeRevokedThroughOwnOrganization() throws Exception {
        // Given
        Organization initech = fixture.organization("Initech QA");
        fixture.member(initech, acme.eve(), OrganizationRole.OWNER);
        Invitation i = seedPendingInvitation(invitationRepository, acmeOrg(), acme.ben(), "fay@example.com",
                OrganizationRole.MEMBER, "inv-idor-QA");
        String path = invitations(initech.getId()) + "/" + i.getId();

        // When
        MvcResult result = mockMvc.perform(as(delete(path), acme.eve())).andReturn();

        // Then
        assertProblem(result, 404, "INVITATION_NOT_FOUND", path);
        assertThat(invitationRepository.findById(i.getId()).orElseThrow().getStatus())
                .isEqualTo(InvitationStatus.PENDING);
    }

    @ParameterizedTest(name = "qa IDOR userId: Initech's owner can't {0} Acme's member Cai through Initech's path")
    @CsvSource({"change the role of, PATCH", "remove, DELETE"})
    @Tag("AC-7.4")
    @Tag("AC-8.5")
    void aMemberOfAnotherOrganizationCantBeTargetedThroughOwnOrganization(String operation, String method)
            throws Exception {
        // Given
        Organization initech = fixture.organization("Initech QA");
        fixture.member(initech, acme.eve(), OrganizationRole.OWNER);
        String path = member(initech.getId(), acme.cai().getId());

        // When
        MvcResult result = mockMvc.perform(request(method, path, acme.eve())).andReturn();

        // Then
        assertProblem(result, 404, "MEMBER_NOT_FOUND", path);
        assertThat(roleInAcme(acme.cai())).isEqualTo(OrganizationRole.MEMBER);
    }

    @Test
    @Tag("AC-4.5")
    @DisplayName("qa D3: X-User-Id is never used for authorization")
    void xUserIdIsIgnored() throws Exception {
        // When
        String path = "/api/orgs/" + acme.orgId();
        MvcResult result = mockMvc.perform(as(get(path), acme.eve())
                .header("X-User-Id", acme.ana().getId().toString())).andReturn();

        // Then
        assertProblem(result, 403, "FORBIDDEN", path);
    }

    @Test
    @Tag("AC-7.6")
    @DisplayName("qa D3: a validly signed token whose orgs claim says OWNER of Acme gives a non-member nothing")
    void theOrgsClaimIsNeverUsedForAuthorization() throws Exception {
        // Given a token for Eve with a forged orgs claim, signed with the shared secret
        String token = Jwts.builder()
                .subject(acme.eve().getId().toString())
                .claim("email", "eve@example.com")
                .claim("orgs", List.of(Map.of("orgId", acme.orgId().toString(), "role", "OWNER")))
                .claim("mfa", false)
                .expiration(Date.from(Instant.now().plus(Duration.ofHours(1))))
                .signWith(TestJwt.key(), Jwts.SIG.HS512)
                .compact();
        String invitations = invitations(acme.orgId());

        // When
        MvcResult view = mockMvc.perform(get("/api/orgs/" + acme.orgId())
                .header(HttpHeaders.AUTHORIZATION, bearer(token))).andReturn();
        MvcResult invite = mockMvc.perform(json(post(invitations).header(HttpHeaders.AUTHORIZATION, bearer(token)),
                "{\"email\":\"fay@example.com\",\"role\":\"OWNER\"}")).andReturn();

        // Then
        assertProblem(view, 403, "FORBIDDEN", "/api/orgs/" + acme.orgId());
        assertProblem(invite, 403, "FORBIDDEN", invitations);
        assertThat(invitationRepository.findAll()).isEmpty();
    }

    private void assertAcmeUnchanged(Invitation i) {
        assertThat(invitationRepository.findById(i.getId()).orElseThrow().getStatus())
                .isEqualTo(InvitationStatus.PENDING);
        assertThat(invitationRepository.findAll()).hasSize(1);
        assertThat(roleInAcme(acme.cai())).isEqualTo(OrganizationRole.MEMBER);
        assertThat(orgMembershipRepository.countByOrganizationId(acme.orgId())).isEqualTo(4);
    }

    private MockHttpServletRequestBuilder request(String method, String path, User actor) {
        return switch (method) {
            case "GET" -> as(get(path), actor);
            case "DELETE" -> as(delete(path), actor);
            case "PATCH" -> json(as(patch(path), actor), "{\"role\":\"READER\"}");
            default -> json(as(post(path), actor), "{\"email\":\"zed@example.com\",\"role\":\"READER\"}");
        };
    }

    private static OrganizationRole acmeRole(String name) {
        return switch (name) {
            case "ana" -> OrganizationRole.OWNER;
            case "ben" -> OrganizationRole.ADMIN;
            case "cai" -> OrganizationRole.MEMBER;
            case "dan" -> OrganizationRole.READER;
            default -> throw new IllegalArgumentException(name + " is not a member of Acme");
        };
    }

    private User user(String name) {
        return switch (name) {
            case "ana" -> acme.ana();
            case "ben" -> acme.ben();
            case "cai" -> acme.cai();
            case "dan" -> acme.dan();
            default -> acme.eve();
        };
    }

    private Organization acmeOrg() {
        return organizationRepository.findById(acme.orgId()).orElseThrow();
    }

    private OrganizationRole roleInAcme(User user) {
        return orgMembershipRepository.findByUserIdAndOrganizationId(user.getId(), acme.orgId())
                .map(OrgMembership::getRole).orElse(null);
    }

    private static String invitations(UUID orgId) {
        return "/api/orgs/" + orgId + "/invitations";
    }

    private static String member(UUID orgId, UUID userId) {
        return "/api/orgs/" + orgId + "/members/" + userId;
    }
}
