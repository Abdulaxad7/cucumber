package com.example.authservice.qa;

import com.example.authservice.enums.OrganizationRole;
import com.example.authservice.model.OrgMembership;
import com.example.authservice.model.Organization;
import com.example.authservice.model.User;
import com.example.authservice.repository.InvitationRepository;
import com.example.authservice.repository.OrgMembershipRepository;
import com.example.authservice.repository.OrganizationRepository;
import com.example.authservice.repository.UserRepository;
import com.example.authservice.support.AcmeFixture;
import com.example.authservice.support.AcmeFixture.Acme;
import com.example.authservice.support.AuthServiceIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import static com.example.authservice.qa.QaSupport.as;
import static com.example.authservice.qa.QaSupport.concurrently;
import static com.example.authservice.qa.QaSupport.json;
import static com.example.authservice.qa.QaSupport.seedPendingInvitation;
import static com.example.authservice.qa.QaSupport.statuses;
import static com.example.authservice.support.ApiCalls.bearer;
import static com.example.authservice.support.ApiCalls.loginBody;
import static com.example.authservice.support.ApiCalls.tokenOf;
import static com.example.authservice.support.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * qa concurrency checks of the NFR "seat limits hold, one token gives one membership" and of D11's last-owner rule,
 * beyond backend-swe's two-request tests. Every test is repeated, and none may ever produce a 500.
 */
@AuthServiceIntegrationTest
class QaConcurrencyTest {

    private static final String NEW_ACCOUNT = "{\"password\":\"Secret123!\",\"fullName\":\"New Person\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AcmeFixture fixture;

    @Autowired
    private UserRepository userRepository;

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

    @RepeatedTest(value = 5, name = "AC-6.9 / NFR (qa) two acceptances racing for the last seat: one joins, one gets "
            + "SEAT_LIMIT_REACHED and leaves no account (run {currentRepetition})")
    @Tag("AC-6.9")
    void twoAcceptancesRacingForTheLastSeat() throws Exception {
        // Given 4 members and two pending invitations seeded over the limit (the API can't create the second one)
        seedPendingInvitation(invitationRepository, acmeOrg(), acme.ben(), "fay@example.com", OrganizationRole.MEMBER,
                "inv-race-fay-QA");
        seedPendingInvitation(invitationRepository, acmeOrg(), acme.ben(), "gus@example.com", OrganizationRole.READER,
                "inv-race-gus-QA");

        // When
        List<MvcResult> results = concurrently(
                () -> acceptAnonymously("inv-race-fay-QA"),
                () -> acceptAnonymously("inv-race-gus-QA"));

        // Then
        assertThat(statuses(results)).containsExactlyInAnyOrder(201, 409);
        MvcResult loser = results.stream().filter(result -> result.getResponse().getStatus() == 409).findFirst()
                .orElseThrow();
        assertThat(loser.getResponse().getContentAsString()).contains("\"code\":\"SEAT_LIMIT_REACHED\"");
        assertThat(orgMembershipRepository.countByOrganizationId(acme.orgId())).isEqualTo(5);
        assertThat(userRepository.findAll()).extracting(User::getEmail)
                .containsOnlyOnce(results.get(0).getResponse().getStatus() == 201 ? "fay@example.com" : "gus@example.com")
                .doesNotContain(results.get(0).getResponse().getStatus() == 201 ? "gus@example.com" : "fay@example.com");
    }

    @RepeatedTest(value = 3, name = "AC-5.3 / NFR (qa) eight invitations racing for the last seat: exactly one is "
            + "created (run {currentRepetition})")
    @Tag("AC-5.3")
    void eightInvitationsRacingForTheLastSeat() throws Exception {
        // When
        List<Callable<MvcResult>> calls = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            User actor = i % 2 == 0 ? acme.ana() : acme.ben();
            String body = "{\"email\":\"racer" + i + "@example.com\",\"role\":\"READER\"}";
            calls.add(() -> mockMvc.perform(json(as(post(invitations()), actor), body)).andReturn());
        }
        List<MvcResult> results = concurrently(calls);

        // Then
        assertThat(statuses(results)).containsOnly(201, 409).containsOnlyOnce(201);
        for (MvcResult result : results) {
            if (result.getResponse().getStatus() == 409) {
                assertProblem(result, 409, "SEAT_LIMIT_REACHED", invitations());
            }
        }
        assertThat(invitationRepository.findAll()).hasSize(1);
    }

    @RepeatedTest(value = 5, name = "AC-5.3 / NFR (qa) an acceptance and an invitation racing on a full organization "
            + "never exceed the seat limit (run {currentRepetition})")
    @Tag("AC-5.3")
    void anAcceptanceAndAnInvitationRacingOnAFullOrganization() throws Exception {
        // Given 4 members and Fay's pending invitation use all 5 seats
        seedPendingInvitation(invitationRepository, acmeOrg(), acme.ben(), "fay@example.com", OrganizationRole.MEMBER,
                "inv-full-QA");

        // When
        List<MvcResult> results = concurrently(
                () -> acceptAnonymously("inv-full-QA"),
                () -> mockMvc.perform(json(as(post(invitations()), acme.ben()),
                        "{\"email\":\"gus@example.com\",\"role\":\"READER\"}")).andReturn());

        // Then
        assertThat(statuses(results)).containsExactly(201, 409);
        assertProblem(results.get(1), 409, "SEAT_LIMIT_REACHED", invitations());
        assertThat(orgMembershipRepository.countByOrganizationId(acme.orgId())).isEqualTo(5);
    }

    @RepeatedTest(value = 5, name = "AC-6.4 / Q5 (qa) a logged-in and an anonymous acceptance of one token: one "
            + "membership, no 500, the password unchanged (run {currentRepetition})")
    @Tag("AC-6.4")
    void aLoggedInAndAnAnonymousAcceptanceOfOneToken() throws Exception {
        // Given Fay has an account and holds a token; Ben invited her
        seedPendingInvitation(invitationRepository, acmeOrg(), acme.ben(), "fay@example.com", OrganizationRole.MEMBER,
                "inv-mixed-QA");
        mockMvc.perform(json(post("/api/auth/register"),
                "{\"email\":\"fay@example.com\",\"password\":\"Secret123!\",\"fullName\":\"Fay Lin\"}"));
        String fayToken = tokenOf(mockMvc, "fay@example.com");

        // When
        List<MvcResult> results = concurrently(
                () -> mockMvc.perform(post(accept("inv-mixed-QA"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(fayToken))).andReturn(),
                () -> mockMvc.perform(json(post(accept("inv-mixed-QA")),
                        "{\"password\":\"Other123!\",\"fullName\":\"Mallory\"}")).andReturn());

        // Then: the logged-in call joins; the anonymous one is EMAIL_ALREADY_REGISTERED (first) or ALREADY_ACCEPTED
        assertThat(results.get(0).getResponse().getStatus()).isEqualTo(200);
        assertThat(results.get(1).getResponse().getStatus()).isIn(409, 410);
        assertThat(results.get(1).getResponse().getContentAsString())
                .containsAnyOf("\"code\":\"EMAIL_ALREADY_REGISTERED\"", "\"code\":\"INVITATION_ALREADY_ACCEPTED\"");
        assertThat(orgMembershipRepository.findAll())
                .filteredOn(membership -> membership.getUser().getEmail().equals("fay@example.com")).hasSize(1);
        assertThat(userRepository.findAll()).filteredOn(user -> user.getEmail().equals("fay@example.com")).hasSize(1);
        assertThat(mockMvc.perform(json(post("/api/auth/login"), loginBody("fay@example.com", "Secret123!")))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    @RepeatedTest(value = 5, name = "AC-7.2 (qa) two admins change one member's role at the same time: both succeed "
            + "in some order, one membership row remains (run {currentRepetition})")
    @Tag("AC-7.2")
    void twoConcurrentRoleChangesOfOneMember() throws Exception {
        // When
        List<MvcResult> results = concurrently(
                () -> mockMvc.perform(json(as(patch(member(acme.cai().getId())), acme.ana()), "{\"role\":\"ADMIN\"}"))
                        .andReturn(),
                () -> mockMvc.perform(json(as(patch(member(acme.cai().getId())), acme.ben()), "{\"role\":\"READER\"}"))
                        .andReturn());

        // Then
        assertThat(statuses(results)).containsExactly(200, 200);
        assertThat(orgMembershipRepository.findAll())
                .filteredOn(membership -> membership.getUser().getId().equals(acme.cai().getId()))
                .singleElement()
                .extracting(OrgMembership::getRole).isIn(OrganizationRole.ADMIN, OrganizationRole.READER);
    }

    @RepeatedTest(value = 5, name = "AC-7.6 (qa) an admin demoted while removing someone: the removal happens only if "
            + "it ran first (run {currentRepetition})")
    @Tag("AC-7.6")
    void aDemotionRacingWithAnAdminAction() throws Exception {
        // When Ana demotes Ben while Ben removes Dan
        List<MvcResult> results = concurrently(
                () -> mockMvc.perform(json(as(patch(member(acme.ben().getId())), acme.ana()), "{\"role\":\"MEMBER\"}"))
                        .andReturn(),
                () -> mockMvc.perform(as(delete(member(acme.dan().getId())), acme.ben())).andReturn());

        // Then
        assertThat(results.get(0).getResponse().getStatus()).isEqualTo(200);
        int removal = results.get(1).getResponse().getStatus();
        assertThat(removal).isIn(204, 403);
        boolean danIsMember = orgMembershipRepository
                .findByUserIdAndOrganizationId(acme.dan().getId(), acme.orgId()).isPresent();
        assertThat(danIsMember).isEqualTo(removal == 403);
        assertThat(roleOf(acme.ben().getId())).isEqualTo(OrganizationRole.MEMBER);
    }

    @RepeatedTest(value = 5, name = "AC-8.4 (qa) two owners removing each other at the same time keep at least one "
            + "owner (run {currentRepetition})")
    @Tag("AC-8.4")
    void twoOwnersRemovingEachOther() throws Exception {
        // Given
        makeBenOwner();

        // When
        List<MvcResult> results = concurrently(
                () -> mockMvc.perform(as(delete(member(acme.ben().getId())), acme.ana())).andReturn(),
                () -> mockMvc.perform(as(delete(member(acme.ana().getId())), acme.ben())).andReturn());

        // Then
        assertThat(statuses(results)).containsExactlyInAnyOrder(204, 403);
        assertThat(orgMembershipRepository.countByOrganizationIdAndRole(acme.orgId(), OrganizationRole.OWNER))
                .isEqualTo(1);
    }

    @RepeatedTest(value = 5, name = "AC-8.4 (qa) an owner leaving while demoting the other owner never leaves the "
            + "organization without an owner (run {currentRepetition})")
    @Tag("AC-8.4")
    void anOwnerLeavingWhileDemotingTheOtherOwner() throws Exception {
        // Given
        makeBenOwner();

        // When Ana leaves and, at the same time, demotes Ben
        List<MvcResult> results = concurrently(
                () -> mockMvc.perform(as(delete(member(acme.ana().getId())), acme.ana())).andReturn(),
                () -> mockMvc.perform(json(as(patch(member(acme.ben().getId())), acme.ana()), "{\"role\":\"ADMIN\"}"))
                        .andReturn());

        // Then: leave first (204, then the demotion is 403), or demote first (200, then LAST_OWNER 409)
        assertThat(statuses(results)).isIn(List.of(204, 403), List.of(409, 200));
        assertThat(orgMembershipRepository.countByOrganizationIdAndRole(acme.orgId(), OrganizationRole.OWNER))
                .isGreaterThanOrEqualTo(1);
    }

    /**
     * Narrowed in the fix round (D13 Q6, AC-2.11): the losers used to be allowed CONFLICT as well; they must now get
     * EMAIL_ALREADY_REGISTERED, also when the racing emails differ only in letter case.
     */
    @RepeatedTest(value = 3, name = "AC-2.2 / AC-2.11 (qa) five simultaneous registrations of one email in mixed letter "
            + "case create one account; every loser is EMAIL_ALREADY_REGISTERED (run {currentRepetition})")
    @Tag("AC-2.2")
    @Tag("AC-2.11")
    void simultaneousRegistrationsOfOneEmail() throws Exception {
        // When
        List<Callable<MvcResult>> calls = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String email = i % 2 == 0 ? "zed@example.com" : "Zed@Example.com";
            calls.add(() -> mockMvc.perform(json(post("/api/auth/register"),
                    "{\"email\":\"" + email + "\",\"password\":\"Secret123!\",\"fullName\":\"Zed\"}")).andReturn());
        }
        List<MvcResult> results = concurrently(calls);

        // Then
        assertThat(statuses(results)).containsOnly(201, 409).containsOnlyOnce(201);
        for (MvcResult result : results) {
            if (result.getResponse().getStatus() == 409) {
                assertProblem(result, 409, "EMAIL_ALREADY_REGISTERED", "/api/auth/register");
            }
        }
        assertThat(userRepository.findAll()).filteredOn(user -> user.getEmail().equals("zed@example.com")).hasSize(1);
    }

    private void makeBenOwner() {
        OrgMembership ben = orgMembershipRepository.findByUserIdAndOrganizationId(acme.ben().getId(), acme.orgId())
                .orElseThrow();
        ben.setRole(OrganizationRole.OWNER);
        orgMembershipRepository.save(ben);
    }

    private OrganizationRole roleOf(UUID userId) {
        return orgMembershipRepository.findByUserIdAndOrganizationId(userId, acme.orgId())
                .map(OrgMembership::getRole).orElse(null);
    }

    private MvcResult acceptAnonymously(String token) throws Exception {
        return mockMvc.perform(post(accept(token)).contentType(MediaType.APPLICATION_JSON).content(NEW_ACCOUNT))
                .andReturn();
    }

    private Organization acmeOrg() {
        return organizationRepository.findById(acme.orgId()).orElseThrow();
    }

    private String invitations() {
        return "/api/orgs/" + acme.orgId() + "/invitations";
    }

    private String member(UUID userId) {
        return "/api/orgs/" + acme.orgId() + "/members/" + userId;
    }

    private static String accept(String token) {
        return "/api/auth/invitations/" + token + "/accept";
    }
}
