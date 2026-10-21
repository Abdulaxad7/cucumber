package com.example.authservice.controller;

import com.example.authservice.repository.InvitationRepository;
import com.example.authservice.repository.OrgMembershipRepository;
import com.example.authservice.repository.UserRepository;
import com.example.authservice.support.AuthServiceIntegrationTest;
import com.example.authservice.support.ProjectFixture;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * US-5 AC-5.3 (phase 4, T3): an invitation token that PostgreSQL can't store or compare (NUL, an overlong NUL, an
 * encoded surrogate) is never a server error. The token is sent percent-encoded in the path, as is.
 */
@AuthServiceIntegrationTest
@Import(ProjectFixture.class)
class UnstorableTokenTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectFixture fixture;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrgMembershipRepository orgMembershipRepository;

    @Autowired
    private InvitationRepository invitationRepository;

    @BeforeEach
    void seedProjects() {
        fixture.seed();
    }

    @ParameterizedTest(name = "AC-5.3 token {0}")
    @ValueSource(strings = {"abc%00def", "abc%C0%80def", "abc%ED%A0%80de"})
    @Tag("AC-5.3")
    void anInvitationTokenThatCantBeStoredIsNeverAServerError(String token) throws Exception {
        // Given
        List<Long> before = counts();

        // When
        MvcResult result = mockMvc.perform(post(URI.create("/api/auth/invitations/" + token + "/accept"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"Secret123!\",\"fullName\":\"Fay Lowe\"}"))
                .andReturn();

        // Then
        int status = result.getResponse().getStatus();
        assertThat(status).as("status").isIn(404, 400);
        if (status == 404) {
            assertThat(JsonPath.<String>read(result.getResponse().getContentAsString(), "$.code"))
                    .isEqualTo("INVITATION_NOT_FOUND");
        }
        assertThat(counts()).as("nothing was stored").isEqualTo(before);
    }

    private List<Long> counts() {
        return List.of(userRepository.count(), orgMembershipRepository.count(), invitationRepository.count());
    }
}
