package com.example.authservice.controller;

import com.example.authservice.enums.OrganizationRole;
import com.example.authservice.model.OrgMembership;
import com.example.authservice.model.Organization;
import com.example.authservice.repository.OrgMembershipRepository;
import com.example.authservice.support.AcmeFixture;
import com.example.authservice.support.AcmeFixture.Acme;
import com.example.authservice.support.AuthServiceIntegrationTest;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.example.authservice.support.ApiCalls.bearer;
import static com.example.authservice.support.ApiCalls.tokenOf;
import static com.example.authservice.support.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * US-4: create, list and view organizations (T11).
 */
@AuthServiceIntegrationTest
class OrganizationsTest {

    private static final String ORGS = "/api/orgs";
    private static final String EVE = "eve@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AcmeFixture fixture;

    @Autowired
    private OrgMembershipRepository orgMembershipRepository;

    private Acme acme;

    @BeforeEach
    void seedAcme() {
        acme = fixture.seed();
    }

    @Test
    @Tag("AC-4.1")
    @DisplayName("AC-4.1 Creating an organization makes the creator its owner (the members-list step is checked "
            + "through the repository here and through GET /api/orgs/{id}/members in MembersTest, T14)")
    void creatingAnOrganizationMakesTheCreatorItsOwner() throws Exception {
        // When
        MvcResult result = createOrganization(tokenOf(mockMvc, EVE), "{\"name\":\"Globex QA\"}");

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        DocumentContext body = JsonPath.parse(result.getResponse().getContentAsString());
        UUID id = UUID.fromString(body.read("$.id", String.class));
        assertThat(body.read("$.name", String.class)).isEqualTo("Globex QA");
        assertThat(body.read("$.plan", String.class)).isEqualTo("FREE");
        assertThat(body.read("$.seatLimit", Integer.class)).isEqualTo(5);
        assertThat(body.read("$.createdAt", String.class)).isNotBlank();
        assertThat(body.read("$.myRole", String.class)).isEqualTo("OWNER");
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo("/api/orgs/" + id);
        assertThat(orgMembershipRepository.findAll())
                .filteredOn(membership -> membership.getOrganization().getId().equals(id))
                .singleElement()
                .satisfies(membership -> {
                    assertThat(membership.getUser().getId()).isEqualTo(acme.eve().getId());
                    assertThat(membership.getRole()).isEqualTo(OrganizationRole.OWNER);
                });
    }

    @ParameterizedTest(name = "AC-4.2 body {0}")
    @ValueSource(strings = {"{}", "{\"name\":\"\"}", "{\"name\":\"   \"}", "LONG_NAME"})
    @Tag("AC-4.2")
    void anInvalidOrganizationNameIsAValidationError(String body) throws Exception {
        // Given
        String content = body.equals("LONG_NAME") ? "{\"name\":\"" + "n".repeat(101) + "\"}" : body;

        // When
        MvcResult result = createOrganization(tokenOf(mockMvc, EVE), content);

        // Then
        DocumentContext problem = assertProblem(result, 400, "VALIDATION_FAILED", ORGS);
        List<Map<String, String>> errors = problem.read("$.errors");
        assertThat(errors).extracting(error -> error.get("field")).containsOnly("name").isNotEmpty();
        assertThat(orgMembershipRepository.findByUser(acme.eve())).isEmpty();
    }

    @Test
    @Tag("AC-4.3")
    @DisplayName("AC-4.3 Listing my organizations shows each one with my role")
    void listingMyOrganizationsShowsEachOneWithMyRole() throws Exception {
        // Given
        Organization initech = fixture.organization("Initech QA");
        fixture.member(initech, acme.eve(), OrganizationRole.OWNER);
        fixture.member(initech, acme.ana(), OrganizationRole.READER);

        // When
        MvcResult result = listOrganizations(tokenOf(mockMvc, "ana@example.com"));

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        DocumentContext body = JsonPath.parse(result.getResponse().getContentAsString());
        assertThat(body.read("$.length()", Integer.class)).isEqualTo(2);
        assertThat(body.read("$[0].name", String.class)).isEqualTo("Acme QA");
        assertThat(body.read("$[0].id", String.class)).isEqualTo(acme.orgId().toString());
        assertThat(body.read("$[0].myRole", String.class)).isEqualTo("OWNER");
        assertThat(body.read("$[1].name", String.class)).isEqualTo("Initech QA");
        assertThat(body.read("$[1].id", String.class)).isEqualTo(initech.getId().toString());
        assertThat(body.read("$[1].myRole", String.class)).isEqualTo("READER");
    }

    @Test
    @Tag("AC-4.4")
    @DisplayName("AC-4.4 A user without organizations gets an empty list")
    void aUserWithoutOrganizationsGetsAnEmptyList() throws Exception {
        // When
        MvcResult result = listOrganizations(tokenOf(mockMvc, EVE));

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString()).isEqualTo("[]");
    }

    @ParameterizedTest(name = "AC-4.5 {0} views Acme: {1}")
    @CsvSource({"ana, OWNER", "ben, ADMIN", "cai, MEMBER", "dan, READER", "eve, FORBIDDEN"})
    @Tag("AC-4.5")
    void viewingOneOrganizationIsAllowedForEveryMemberAndNobodyElse(String user, String outcome) throws Exception {
        // Given
        String path = ORGS + "/" + acme.orgId();

        // When
        MvcResult result = mockMvc.perform(get(path)
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenOf(mockMvc, user + "@example.com")))).andReturn();

        // Then
        if (outcome.equals("FORBIDDEN")) {
            assertProblem(result, 403, "FORBIDDEN", path);
        } else {
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            DocumentContext body = JsonPath.parse(result.getResponse().getContentAsString());
            assertThat(body.read("$.id", String.class)).isEqualTo(acme.orgId().toString());
            assertThat(body.read("$.name", String.class)).isEqualTo("Acme QA");
            assertThat(body.read("$.plan", String.class)).isEqualTo("FREE");
            assertThat(body.read("$.seatLimit", Integer.class)).isEqualTo(5);
            assertThat(body.read("$.myRole", String.class)).isEqualTo(outcome);
        }
    }

    @ParameterizedTest(name = "AC-4.6 id {0}: {1} {2}")
    @CsvSource({"UNKNOWN, 403, FORBIDDEN", "not-a-uuid, 400, MALFORMED_REQUEST"})
    @Tag("AC-4.6")
    void anUnknownOrMalformedOrganizationIdDoesntRevealAnything(String id, int status, String code) throws Exception {
        // Given
        String path = ORGS + "/" + (id.equals("UNKNOWN") ? AcmeFixture.UNKNOWN_ID : id);

        // When
        MvcResult result = mockMvc.perform(get(path)
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenOf(mockMvc, EVE)))).andReturn();

        // Then
        assertProblem(result, status, code, path);
    }

    @Test
    @Tag("AC-4.7")
    @DisplayName("AC-4.7 A new organization is usable without logging in again")
    void aNewOrganizationIsUsableWithoutLoggingInAgain() throws Exception {
        // Given Eve holds a token issued while she belonged to no organization
        String token = tokenOf(mockMvc, EVE);
        MvcResult created = createOrganization(token, "{\"name\":\"Globex QA\"}");
        String globexId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        // When
        MvcResult list = listOrganizations(token);
        MvcResult view = mockMvc.perform(get(ORGS + "/" + globexId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andReturn();

        // Then
        assertThat(list.getResponse().getStatus()).isEqualTo(200);
        DocumentContext body = JsonPath.parse(list.getResponse().getContentAsString());
        assertThat(body.read("$[?(@.name == 'Globex QA')].myRole", List.class)).containsExactly("OWNER");
        assertThat(view.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Duplicate organization names are allowed (D11)")
    void duplicateNamesAreAllowed() throws Exception {
        // When
        MvcResult result = createOrganization(tokenOf(mockMvc, EVE), "{\"name\":\"Acme QA\"}");

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        List<OrgMembership> eveMemberships = orgMembershipRepository.findByUser(acme.eve());
        assertThat(eveMemberships).singleElement()
                .satisfies(membership -> assertThat(membership.getOrganization().getId()).isNotEqualTo(acme.orgId()));
    }

    private MvcResult createOrganization(String token, String body) throws Exception {
        return mockMvc.perform(post(ORGS).header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    private MvcResult listOrganizations(String token) throws Exception {
        return mockMvc.perform(get(ORGS).header(HttpHeaders.AUTHORIZATION, bearer(token))).andReturn();
    }
}
