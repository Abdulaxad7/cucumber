package com.example.authservice.qa;

import com.example.authservice.repository.UserRepository;
import com.example.authservice.support.AcmeFixture;
import com.example.authservice.support.AcmeFixture.Acme;
import com.example.authservice.support.AuthServiceIntegrationTest;
import com.example.authservice.support.TestJwt;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static com.example.authservice.qa.QaSupport.json;
import static com.example.authservice.qa.QaSupport.read;
import static com.example.authservice.support.ApiCalls.bearer;
import static com.example.authservice.support.ApiCalls.loginBody;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * qa leakage check over the whole phase-1 flow, including failures: passwords, password hashes, access tokens,
 * acceptance tokens and the signing secret never appear in the log or in a response that shouldn't carry them, and
 * the INFO business log lines carry ids only (NFR "Audit").
 */
@AuthServiceIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class QaLeakageTest {

    private static final String FAY_PASSWORD = "Fay-Pass-QA-9!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AcmeFixture fixture;

    @Autowired
    private UserRepository userRepository;

    private Acme acme;

    @BeforeEach
    void seedAcme() {
        acme = fixture.seed();
    }

    @Test
    @Tag("AC-3.8")
    @Tag("AC-5.1")
    @DisplayName("qa: no password, hash, token or secret in the log or in responses during the full flow")
    void nothingSecretLeaksDuringTheFullFlow(CapturedOutput output) throws Exception {
        int logStart = output.getAll().length();
        List<MvcResult> responses = new ArrayList<>();

        // register, wrong logins, login
        responses.add(perform(json(post("/api/auth/register"),
                "{\"email\":\"gus@example.com\",\"password\":\"" + FAY_PASSWORD + "\",\"fullName\":\"Gus Ray\"}")));
        responses.add(perform(json(post("/api/auth/login"), loginBody("ana@example.com", FAY_PASSWORD))));
        responses.add(perform(json(post("/api/auth/login"), loginBody("nobody@example.com", FAY_PASSWORD))));
        MvcResult anaLogin = perform(json(post("/api/auth/login"), loginBody("ana@example.com", AcmeFixture.PASSWORD)));
        String anaToken = read(anaLogin, "$.accessToken");

        // create an organization, invite, accept, change the role, remove, list
        MvcResult created = perform(json(post("/api/orgs").header(HttpHeaders.AUTHORIZATION, bearer(anaToken)),
                "{\"name\":\"Globex QA\"}"));
        String globex = "/api/orgs/" + read(created, "$.id");
        MvcResult invited = perform(json(post(globex + "/invitations").header(HttpHeaders.AUTHORIZATION,
                bearer(anaToken)), "{\"email\":\"fay@example.com\",\"role\":\"MEMBER\"}"));
        String invitationToken = read(invited, "$.token");
        responses.add(perform(json(post("/api/auth/invitations/" + invitationToken + "/accept"),
                "{\"password\":\"short\",\"fullName\":\"Fay Lin\"}")));
        MvcResult accepted = perform(json(post("/api/auth/invitations/" + invitationToken + "/accept"),
                "{\"password\":\"" + FAY_PASSWORD + "\",\"fullName\":\"Fay Lin\"}"));
        responses.add(accepted);
        String fayId = read(accepted, "$.user.id");
        responses.add(perform(json(patch(globex + "/members/" + fayId)
                .header(HttpHeaders.AUTHORIZATION, bearer(anaToken)), "{\"role\":\"READER\"}")));
        responses.add(perform(get(globex + "/members").header(HttpHeaders.AUTHORIZATION, bearer(anaToken))));
        responses.add(perform(delete(globex + "/members/" + fayId).header(HttpHeaders.AUTHORIZATION, bearer(anaToken))));
        responses.add(perform(get("/api/orgs").header(HttpHeaders.AUTHORIZATION, bearer(anaToken))));
        responses.add(perform(get(globex + "/invitations").header(HttpHeaders.AUTHORIZATION, bearer(anaToken))));
        // bad tokens
        responses.add(perform(get("/api/orgs").header(HttpHeaders.AUTHORIZATION, bearer(anaToken + "x"))));
        responses.add(perform(get("/api/orgs").header(HttpHeaders.AUTHORIZATION,
                bearer(TestJwt.signedWithHs256(acme.ana().getId(), "ana@example.com")))));

        // Then: the log
        String log = output.getAll().substring(logStart);
        String hash = userRepository.findByEmail("gus@example.com").orElseThrow().getPasswordHash();
        assertThat(log).doesNotContain(FAY_PASSWORD, AcmeFixture.PASSWORD, hash, anaToken, invitationToken,
                TestJwt.SECRET, "$2a$", "$2b$", "Bearer ");
        // NFR: the business INFO lines carry ids, never emails
        assertThat(log).contains("Organization ", "Invitation ", "accepted by user", "removed from organization");
        assertThat(log).doesNotContain("fay@example.com", "gus@example.com", "ana@example.com");

        // Then: no response except the login and the create-invitation responses carries a secret
        // (A Problem's instance is the request path (D2), which for the accept operation holds the token the caller
        // sent; it is removed before the check. See the QA report, open question on instance.)
        for (MvcResult response : responses) {
            String body = response.getResponse().getContentAsString();
            if (body.contains("\"instance\"")) {
                body = JsonPath.parse(body).delete("$.instance").jsonString();
            }
            assertThat(body)
                    .doesNotContain(FAY_PASSWORD, hash, "$2a$", "$2b$", invitationToken, TestJwt.SECRET)
                    .doesNotContainIgnoringCase("passwordHash");
        }
    }

    private MvcResult perform(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        return mockMvc.perform(request).andReturn();
    }
}
