package com.example.authservice.qa;

import com.example.authservice.enums.InvitationStatus;
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
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.example.authservice.qa.QaSupport.as;
import static com.example.authservice.qa.QaSupport.json;
import static com.example.authservice.qa.QaSupport.read;
import static com.example.authservice.support.ApiCalls.bearer;
import static com.example.authservice.support.ApiCalls.loginBody;
import static com.example.authservice.support.ApiCalls.tokenOf;
import static com.example.authservice.support.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * qa invitation security: single use, replay after accept/revoke/expiry, wrong account, what an anonymous caller
 * can't choose, token format and uniqueness, and where the acceptance token may and may not appear.
 */
@AuthServiceIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class QaInvitationSecurityTest {

    private static final String FAY = "fay@example.com";
    private static final String NEW_ACCOUNT = "{\"password\":\"Secret123!\",\"fullName\":\"Fay Lin\"}";

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
    private String invitationId;
    private String t;

    @BeforeEach
    void seedAcmeAndInviteFay() throws Exception {
        acme = fixture.seed();
        MvcResult invited = mockMvc.perform(json(as(post(invitations()), acme.ben()),
                "{\"email\":\"fay@example.com\",\"role\":\"MEMBER\"}")).andReturn();
        assertThat(invited.getResponse().getStatus()).isEqualTo(201);
        invitationId = read(invited, "$.id");
        t = read(invited, "$.token");
    }

    @Test
    @Tag("AC-6.4")
    @DisplayName("qa replay: a token accepted anonymously can't be used again, anonymously or logged in")
    void anAcceptedTokenCantBeReplayed() throws Exception {
        // Given
        assertThat(acceptAnonymously(t, NEW_ACCOUNT).getResponse().getStatus()).isEqualTo(201);

        // When
        MvcResult anonymousReplay = acceptAnonymously(t, "{\"password\":\"Other123!\",\"fullName\":\"Mallory\"}");
        MvcResult fayReplay = mockMvc.perform(post(accept(t))
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenOf(mockMvc, FAY)))).andReturn();
        MvcResult eveReplay = mockMvc.perform(as(post(accept(t)), acme.eve())).andReturn();

        // Then
        assertProblem(anonymousReplay, 410, "INVITATION_ALREADY_ACCEPTED", accept(t));
        assertProblem(fayReplay, 410, "INVITATION_ALREADY_ACCEPTED", accept(t));
        assertProblem(eveReplay, 410, "INVITATION_ALREADY_ACCEPTED", accept(t));
        assertThat(userRepository.findAll()).filteredOn(user -> user.getEmail().equals(FAY)).hasSize(1);
        assertThat(login(FAY, "Secret123!").getResponse().getStatus()).as("password unchanged").isEqualTo(200);
        assertThat(orgMembershipRepository.findByUserIdAndOrganizationId(acme.eve().getId(), acme.orgId())).isEmpty();
    }

    @Test
    @Tag("AC-6.3")
    @DisplayName("qa: no account other than the invited email can accept, whatever its role elsewhere")
    void onlyTheInvitedEmailCanAccept() throws Exception {
        for (User other : List.of(acme.ana(), acme.ben(), acme.cai(), acme.dan(), acme.eve())) {
            assertProblem(mockMvc.perform(as(post(accept(t)), other)).andReturn(), 403, "INVITATION_EMAIL_MISMATCH",
                    accept(t));
        }
        assertThat(invitationRepository.findByToken(t).orElseThrow().getStatus()).isEqualTo(InvitationStatus.PENDING);
        assertThat(orgMembershipRepository.countByOrganizationId(acme.orgId())).isEqualTo(4);
    }

    @Test
    @Tag("AC-6.1")
    @DisplayName("qa: an anonymous caller can't choose the email, role, status or organization of the new account")
    void anAnonymousCallerCantChooseAnythingButPasswordAndName() throws Exception {
        // When
        MvcResult result = acceptAnonymously(t, "{\"password\":\"Secret123!\",\"fullName\":\"Fay Lin\","
                + "\"email\":\"mallory@example.com\",\"role\":\"OWNER\",\"status\":\"ACTIVE\",\"mfaEnabled\":true,"
                + "\"orgId\":\"" + acme.orgId() + "\",\"organization\":{\"id\":\"" + AcmeFixture.UNKNOWN_ID + "\"}}");

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        DocumentContext body = JsonPath.parse(result.getResponse().getContentAsString());
        assertThat(body.read("$.user.email", String.class)).isEqualTo(FAY);
        assertThat(body.read("$.user.status", String.class)).isEqualTo("PENDING_VERIFICATION");
        assertThat(body.read("$.user.mfaEnabled", Boolean.class)).isFalse();
        assertThat(body.read("$.organization.myRole", String.class)).isEqualTo("MEMBER");
        assertThat(userRepository.findByEmail("mallory@example.com")).isEmpty();
        User fay = userRepository.findByEmail(FAY).orElseThrow();
        assertThat(orgMembershipRepository.findByUserIdAndOrganizationId(fay.getId(), acme.orgId()))
                .get().extracting(OrgMembership::getRole).isEqualTo(OrganizationRole.MEMBER);
    }

    @Test
    @Tag("AC-6.5")
    @DisplayName("qa: an anonymous acceptance for an existing account answers the same with the right or a wrong "
            + "password, so it can't be used to guess passwords")
    void anAnonymousAcceptanceIsNoPasswordOracle() throws Exception {
        // Given
        mockMvc.perform(json(post("/api/auth/register"),
                "{\"email\":\"fay@example.com\",\"password\":\"Secret123!\",\"fullName\":\"Fay Lin\"}"));

        // When
        MvcResult right = acceptAnonymously(t, NEW_ACCOUNT);
        MvcResult wrong = acceptAnonymously(t, "{\"password\":\"Wrong123!\",\"fullName\":\"Fay Lin\"}");

        // Then
        assertProblem(right, 409, "EMAIL_ALREADY_REGISTERED", accept(t));
        assertThat(wrong.getResponse().getContentAsString()).isEqualTo(right.getResponse().getContentAsString());
        assertThat(invitationRepository.findByToken(t).orElseThrow().getStatus()).isEqualTo(InvitationStatus.PENDING);
    }

    @Test
    @Tag("AC-6.4")
    @DisplayName("qa enumeration: unknown tokens of any shape get one identical answer that doesn't echo the token "
            + "outside instance")
    void unknownTokensAllLookTheSame() throws Exception {
        // Given: a guess, a near-miss of the real token, a revoked-looking and a very long token
        String nearMiss = t.substring(0, t.length() - 1) + (t.endsWith("A") ? "B" : "A");
        List<String> guesses = List.of("inv-unknown-QA", nearMiss, t.toUpperCase(), "x".repeat(2_000));

        // When / Then
        String detail = null;
        for (String guess : guesses) {
            MvcResult result = acceptAnonymously(guess, NEW_ACCOUNT);
            DocumentContext problem = assertProblem(result, 404, "INVITATION_NOT_FOUND", accept(guess));
            String guessDetail = problem.read("$.detail", String.class);
            assertThat(guessDetail).doesNotContain(guess);
            if (detail == null) {
                detail = guessDetail;
            }
            assertThat(guessDetail).isEqualTo(detail);
        }
        assertThat(userRepository.findByEmail(FAY)).isEmpty();
    }

    @Test
    @Tag("AC-5.1")
    @DisplayName("qa: tokens are 32 URL-safe characters (24 random bytes) and never repeat")
    void tokensAreLongRandomAndUnique() throws Exception {
        // Given room for 30 more invitations
        Organization stored = organizationRepository.findById(acme.orgId()).orElseThrow();
        stored.setSeatLimit(100);
        organizationRepository.save(stored);

        // When
        Set<String> tokens = new HashSet<>();
        tokens.add(t);
        for (int i = 0; i < 30; i++) {
            MvcResult result = mockMvc.perform(json(as(post(invitations()), acme.ana()),
                    "{\"email\":\"user" + i + "@example.com\",\"role\":\"READER\"}")).andReturn();
            assertThat(result.getResponse().getStatus()).isEqualTo(201);
            tokens.add(read(result, "$.token"));
        }

        // Then
        assertThat(tokens).hasSize(31).allSatisfy(token -> {
            assertThat(token).hasSize(32).matches("^[A-Za-z0-9_-]+$");
            assertThat(Base64.getUrlDecoder().decode(token)).hasSize(24);
        });
    }

    @Test
    @Tag("AC-5.8")
    @DisplayName("qa: the token never appears in lists, organization or member responses, Problem details, or the log")
    void theTokenAppearsOnlyInTheCreateResponse(CapturedOutput output) throws Exception {
        // When: every read operation, a failed acceptance by the wrong account, a successful one, and a replay
        List<MvcResult> responses = List.of(
                mockMvc.perform(as(get(invitations()), acme.ana())).andReturn(),
                mockMvc.perform(as(get("/api/orgs/" + acme.orgId()), acme.ana())).andReturn(),
                mockMvc.perform(as(get("/api/orgs"), acme.ana())).andReturn(),
                mockMvc.perform(as(get("/api/orgs/" + acme.orgId() + "/members"), acme.ana())).andReturn(),
                mockMvc.perform(as(post(accept(t)), acme.eve())).andReturn(),
                acceptAnonymously(t, NEW_ACCOUNT),
                acceptAnonymously(t, NEW_ACCOUNT),
                mockMvc.perform(as(delete(invitations() + "/" + invitationId), acme.ben())).andReturn());

        // Then
        for (MvcResult response : responses) {
            String body = response.getResponse().getContentAsString();
            if (body.contains("\"instance\"")) {
                // A Problem may echo the request path in instance (D2), which the caller sent; nothing else may hold it.
                String withoutInstance = JsonPath.parse(body).delete("$.instance").jsonString();
                assertThat(withoutInstance).doesNotContain(t);
            } else {
                assertThat(body).doesNotContain(t);
            }
            assertThat(response.getResponse().getHeaderNames()).allSatisfy(name ->
                    assertThat(response.getResponse().getHeaders(name)).noneSatisfy(value ->
                            assertThat(value).contains(t)));
        }
        assertThat(output.getAll()).doesNotContain(t);
    }

    @Test
    @Tag("AC-6.4")
    @DisplayName("qa: an invitation that expires while pending can't be accepted by a logged-in invitee either")
    void anExpiredInvitationCantBeAcceptedWhileLoggedIn() throws Exception {
        // Given
        mockMvc.perform(json(post("/api/auth/register"),
                "{\"email\":\"fay@example.com\",\"password\":\"Secret123!\",\"fullName\":\"Fay Lin\"}"));
        var invitation = invitationRepository.findByToken(t).orElseThrow();
        invitation.setExpiresAt(Instant.now().minusSeconds(1));
        invitationRepository.save(invitation);

        // When
        MvcResult result = mockMvc.perform(post(accept(t))
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenOf(mockMvc, FAY)))).andReturn();

        // Then
        assertProblem(result, 410, "INVITATION_EXPIRED", accept(t));
        User fay = userRepository.findByEmail(FAY).orElseThrow();
        assertThat(orgMembershipRepository.findByUserIdAndOrganizationId(fay.getId(), acme.orgId())).isEmpty();
    }

    @Test
    @Tag("AC-6.2")
    @DisplayName("qa: the accept response never contains a login token or a password hash")
    void theAcceptResponseHasNoCredentials() throws Exception {
        String body = acceptAnonymously(t, NEW_ACCOUNT).getResponse().getContentAsString();

        assertThat(body).doesNotContainIgnoringCase("accessToken").doesNotContain("$2a$").doesNotContain("$2b$")
                .doesNotContainIgnoringCase("password");
    }

    private String invitations() {
        return "/api/orgs/" + acme.orgId() + "/invitations";
    }

    private static String accept(String token) {
        return "/api/auth/invitations/" + token + "/accept";
    }

    private MvcResult acceptAnonymously(String token, String body) throws Exception {
        return mockMvc.perform(post(accept(token)).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    private MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, password))).andReturn();
    }
}
