package com.example.authservice.controller;

import com.example.authservice.enums.InvitationStatus;
import com.example.authservice.model.Invitation;
import com.example.authservice.repository.InvitationRepository;
import com.example.authservice.support.AcmeFixture;
import com.example.authservice.support.AcmeFixture.Acme;
import com.example.authservice.support.AuthServiceIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.example.authservice.support.ApiCalls.bearer;
import static com.example.authservice.support.ApiCalls.tokenOf;
import static com.example.authservice.support.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * AC-5.12: revoking applies the role ceiling (D13, Q1). Revoke error order: 403 FORBIDDEN, 404 INVITATION_NOT_FOUND,
 * 409 INVITATION_NOT_PENDING, then 422 ROLE_CEILING. The Q4 rule (an expired PENDING invitation can be revoked)
 * applies within the ceiling.
 */
@AuthServiceIntegrationTest
class RevokeCeilingTest {

    private static final String ANA = "ana@example.com";
    private static final String BEN = "ben@example.com";
    private static final String CAI = "cai@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AcmeFixture fixture;

    @Autowired
    private InvitationRepository invitationRepository;

    private Acme acme;

    @BeforeEach
    void seedAcme() {
        acme = fixture.seed();
    }

    @Test
    @Tag("AC-5.12")
    @DisplayName("AC-5.12 row Ben: an ADMIN can't revoke an OWNER invitation (422 ROLE_CEILING), and it stays listed")
    void anAdminCantRevokeAnOwnerInvitation() throws Exception {
        // Given
        String i = invite(ANA, "fay@example.com", "OWNER");

        // When
        MvcResult result = revoke(BEN, i);

        // Then
        assertProblem(result, 422, "ROLE_CEILING", invitation(i));
        assertThat(pendingEmailsListedBy(ANA)).containsExactly("fay@example.com");
    }

    @Test
    @Tag("AC-5.12")
    @DisplayName("AC-5.12 row Ana: an OWNER revokes an OWNER invitation (204)")
    void anOwnerRevokesAnOwnerInvitation() throws Exception {
        // Given
        String i = invite(ANA, "fay@example.com", "OWNER");

        // When
        MvcResult result = revoke(ANA, i);

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(pendingEmailsListedBy(ANA)).isEmpty();
    }

    @Test
    @Tag("AC-5.12")
    @DisplayName("AC-5.12 the ceiling is \"at most the actor's rank\": an ADMIN revokes an ADMIN invitation (204)")
    void anAdminRevokesAnInvitationOfTheirOwnRank() throws Exception {
        // Given
        String i = invite(ANA, "fay@example.com", "ADMIN");

        // When
        MvcResult result = revoke(BEN, i);

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(204);
    }

    @Test
    @Tag("AC-5.12")
    @DisplayName("AC-5.12 D13 order: FORBIDDEN comes before ROLE_CEILING")
    void forbiddenComesBeforeTheCeiling() throws Exception {
        // Given
        String i = invite(ANA, "fay@example.com", "OWNER");

        // When
        MvcResult result = revoke(CAI, i);

        // Then
        assertProblem(result, 403, "FORBIDDEN", invitation(i));
    }

    @Test
    @Tag("AC-5.12")
    @DisplayName("AC-5.12 D13 order: INVITATION_NOT_FOUND comes before ROLE_CEILING")
    void notFoundComesBeforeTheCeiling() throws Exception {
        // When
        MvcResult result = revoke(BEN, AcmeFixture.UNKNOWN_ID.toString());

        // Then
        assertProblem(result, 404, "INVITATION_NOT_FOUND", invitation(AcmeFixture.UNKNOWN_ID.toString()));
    }

    @Test
    @Tag("AC-5.12")
    @DisplayName("AC-5.12 D13 order: INVITATION_NOT_PENDING comes before ROLE_CEILING")
    void notPendingComesBeforeTheCeiling() throws Exception {
        // Given
        String i = invite(ANA, "fay@example.com", "OWNER");
        assertThat(revoke(ANA, i).getResponse().getStatus()).isEqualTo(204);

        // When
        MvcResult result = revoke(BEN, i);

        // Then
        assertProblem(result, 409, "INVITATION_NOT_PENDING", invitation(i));
    }

    @Test
    @Tag("AC-5.12")
    @DisplayName("AC-5.12 Q4 applies within the ceiling: an ADMIN can't revoke an expired PENDING OWNER invitation")
    void anExpiredOwnerInvitationIsStillAboveTheCeiling() throws Exception {
        // Given
        String i = invite(ANA, "fay@example.com", "OWNER");
        Invitation stored = invitationRepository.findById(UUID.fromString(i)).orElseThrow();
        stored.setExpiresAt(LocalDateTime.now().minusHours(1));
        invitationRepository.save(stored);

        // When
        MvcResult result = revoke(BEN, i);

        // Then
        assertProblem(result, 422, "ROLE_CEILING", invitation(i));
        assertThat(invitationRepository.findById(UUID.fromString(i))).get().extracting(Invitation::getStatus)
                .isEqualTo(InvitationStatus.PENDING);
    }

    private String invite(String actor, String email, String role) throws Exception {
        MvcResult result = mockMvc.perform(post(invitations())
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenOf(mockMvc, actor)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"role\":\"" + role + "\"}"))
                .andReturn();
        assertThat(result.getResponse().getStatus()).as("invitation of " + email).isEqualTo(201);
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private MvcResult revoke(String actor, String invitationId) throws Exception {
        return mockMvc.perform(delete(invitation(invitationId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenOf(mockMvc, actor))))
                .andReturn();
    }

    private List<String> pendingEmailsListedBy(String actor) throws Exception {
        MvcResult result = mockMvc.perform(get(invitations())
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenOf(mockMvc, actor))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return JsonPath.read(result.getResponse().getContentAsString(), "$[*].email");
    }

    private String invitations() {
        return "/api/orgs/" + acme.orgId() + "/invitations";
    }

    private String invitation(String invitationId) {
        return invitations() + "/" + invitationId;
    }
}
