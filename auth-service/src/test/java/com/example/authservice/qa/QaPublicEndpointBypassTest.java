package com.example.authservice.qa;

import com.example.authservice.repository.UserRepository;
import com.example.authservice.support.AcmeFixture;
import com.example.authservice.support.AuthServiceIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static com.example.authservice.support.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * qa: method and path variants that try to reach something other than the three public operations anonymously
 * (D7, AC-3.6). A variant is never served: it is either a 401 Problem, or rejected by Spring Security's request
 * firewall (400; see the QA report for the body of that answer on a running server).
 */
@AuthServiceIntegrationTest
class QaPublicEndpointBypassTest {

    private static final String REGISTRATION =
            "{\"email\":\"zed@example.com\",\"password\":\"Secret123!\",\"fullName\":\"Zed Qa\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AcmeFixture fixture;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void seedAcme() {
        fixture.seed();
    }

    @ParameterizedTest(name = "AC-3.6 (qa) anonymous {0} {1} is a 401 Problem")
    @CsvSource({
            "GET,     /api/auth/register",
            "PUT,     /api/auth/register",
            "PATCH,   /api/auth/register",
            "DELETE,  /api/auth/register",
            "HEAD,    /api/auth/register",
            "OPTIONS, /api/auth/register",
            "PUT,     /api/auth/login",
            "DELETE,  /api/auth/login",
            "OPTIONS, /api/auth/login",
            "PUT,     /api/auth/invitations/inv-7Kq2/accept",
            "DELETE,  /api/auth/invitations/inv-7Kq2/accept",
            "POST,    /api/auth/register/",
            "POST,    /api/auth/login/",
            "POST,    /API/AUTH/REGISTER",
            "POST,    /api/auth/Register",
            "POST,    /api/auth/register.json",
            "POST,    /api/auth/invitations/inv-7Kq2/accept/",
            "POST,    /api/auth/invitations/inv-7Kq2/accept/extra",
            "POST,    /api/auth/invitations/inv-7Kq2",
            "POST,    /api/auth/invitations",
            "POST,    /api/auth/invitations/a/b/accept",
            "POST,    /api/auth",
            "POST,    /api/auth/",
            "GET,     /api//orgs",
            "GET,     /api/orgs/",
            "POST,    /swagger-ui/../api/orgs",
            "GET,     /h2-console/login.jsp",
            "GET,     /actuator",
            "GET,     /api/v1/auth/register"
    })
    @Tag("AC-3.6")
    void aVariantOfAPublicOperationIsNotPublic(String method, String path) throws Exception {
        // When
        MvcResult result = mockMvc.perform(request(HttpMethod.valueOf(method), path)
                .contentType(MediaType.APPLICATION_JSON).content(REGISTRATION)).andReturn();

        // Then
        int status = result.getResponse().getStatus();
        assertThat(status).as("never served anonymously").isIn(400, 401);
        if (status == 401 && !method.equals("HEAD")) {
            assertThat(result.getResponse().getContentType()).isEqualTo("application/problem+json");
            assertThat(result.getResponse().getContentAsString()).contains("\"code\":\"UNAUTHENTICATED\"");
        }
        assertThat(userRepository.findByEmail("zed@example.com")).isEmpty();
    }

    @ParameterizedTest(name = "AC-3.6 (qa) the firewall rejects {0}")
    @ValueSource(strings = {
            "/api/auth/register;x=1",
            "/api/auth/./register",
            "/api/auth/x/../register",
            "/api/auth/register%2F",
            "/api/auth/invitations/a%2Fb/accept",
            "/api/auth/invitations/inv-7Kq2/accept;jsessionid=1",
            "/api/auth/invitations/../../orgs/accept",
            "/api/auth/invitations/x/accept/../../../../orgs"
    })
    @Tag("AC-3.6")
    void suspiciousPathsAreRejectedBeforeAnyHandler(String path) throws Exception {
        // When
        MvcResult result = mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(REGISTRATION))
                .andReturn();

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResolvedException()).isNull();
        assertThat(userRepository.findByEmail("zed@example.com")).isEmpty();
    }

    @Test
    @Tag("AC-3.6")
    @DisplayName("qa: TRACE is rejected, so it can't echo an Authorization header")
    void traceIsRejected() throws Exception {
        MvcResult result = mockMvc.perform(request(HttpMethod.TRACE, "/api/orgs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer qa-trace-marker")).andReturn();

        assertThat(result.getResponse().getStatus()).isIn(400, 401, 405);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("qa-trace-marker");
    }

    @Test
    @Tag("AC-3.6")
    @DisplayName("qa: a public operation stays exactly the operation it is; the query string doesn't widen it")
    void aQueryStringDoesntChangeWhatIsPublic() throws Exception {
        MvcResult orgs = mockMvc.perform(get("/api/orgs").param("x", "/api/auth/register")).andReturn();

        assertProblem(orgs, 401, "UNAUTHENTICATED", "/api/orgs");
    }

    @Test
    @Tag("AC-3.7")
    @DisplayName("qa: the public API docs don't open other paths under /v3/api-docs/")
    void theApiDocsPrefixOpensNothingElse() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs/../../api/orgs")).andReturn();

        assertThat(result.getResponse().getStatus()).isIn(400, 401);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("Acme QA");
    }
}
