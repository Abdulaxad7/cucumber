package com.example.authservice.exception;

import com.example.authservice.repository.UserRepository;
import com.example.authservice.support.AcmeFixture;
import com.example.authservice.support.AcmeFixture.Acme;
import com.example.authservice.support.AuthServiceIntegrationTest;
import com.jayway.jsonpath.DocumentContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.example.authservice.support.ApiCalls.bearer;
import static com.example.authservice.support.ApiCalls.loginBody;
import static com.example.authservice.support.ApiCalls.tokenOf;
import static com.example.authservice.support.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * US-1: every error is an RFC 9457 Problem with a stable {@code code} and the real status (D2).
 */
@AuthServiceIntegrationTest
class ProblemDetailsTest {

    private static final String REGISTER = "/api/auth/register";
    private static final String LOGIN = "/api/auth/login";
    private static final String ANA_REGISTRATION =
            "{\"email\":\"ana@example.com\",\"password\":\"Secret123!\",\"fullName\":\"Ana Lima\"}";

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
    @Tag("AC-1.1")
    @DisplayName("AC-1.1 A validation error is a 400 Problem that lists each invalid field")
    void aValidationErrorIsA400ProblemThatListsEachInvalidField() throws Exception {
        // When
        MvcResult result = mockMvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"short\",\"fullName\":\"\"}"))
                .andReturn();

        // Then
        DocumentContext body = assertProblem(result, 400, "VALIDATION_FAILED", REGISTER);
        List<Map<String, String>> errors = body.read("$.errors");
        assertThat(errors).extracting(error -> error.get("field"))
                .containsExactlyInAnyOrder("email", "password", "fullName");
        assertThat(errors).allSatisfy(error -> assertThat(error.get("message")).isNotBlank());
        assertThat(userRepository.findByEmail("not-an-email")).isEmpty();
    }

    @ParameterizedTest(name = "AC-1.2 POST {0}")
    @ValueSource(strings = {"/api/auth/register", "/api/auth/login", "/api/auth/invitations/inv-7Kq2/accept"})
    @Tag("AC-1.2")
    void anUnreadableBodyOnAPublicOperationIsA400Problem(String path) throws Exception {
        // When
        MvcResult result = mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{\"email\":"))
                .andReturn();

        // Then
        assertProblem(result, 400, "MALFORMED_REQUEST", path);
    }

    @Test
    @Tag("AC-1.4")
    @DisplayName("AC-1.4 An unknown path is a 404 Problem for an authenticated caller")
    void anUnknownPathIsA404ProblemForAnAuthenticatedCaller() throws Exception {
        // Given
        String path = "/api/orgs/" + acme.orgId() + "/no-such-thing";

        // When
        MvcResult result = mockMvc.perform(get(path)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenOf(mockMvc, "ana@example.com"))))
                .andReturn();

        // Then
        assertProblem(result, 404, "NOT_FOUND", path);
    }

    @ParameterizedTest(name = "AC-1.5 {0} /api/orgs{1}")
    @CsvSource({"DELETE, ''", "PUT, /{acme}"})
    @Tag("AC-1.5")
    void anUnsupportedMethodIsA405Problem(String method, String suffix) throws Exception {
        // Given
        String path = "/api/orgs" + suffix.replace("{acme}", acme.orgId().toString());
        MockHttpServletRequestBuilder request = method.equals("DELETE") ? delete(path) : put(path);

        // When
        MvcResult result = mockMvc.perform(request
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenOf(mockMvc, "ana@example.com"))))
                .andReturn();

        // Then
        assertProblem(result, 405, "METHOD_NOT_ALLOWED", path);
    }

    @Test
    @Tag("AC-1.6")
    @DisplayName("AC-1.6 An unsupported content type is a 415 Problem")
    void anUnsupportedContentTypeIsA415Problem() throws Exception {
        // When
        MvcResult result = mockMvc.perform(post(LOGIN).contentType(MediaType.TEXT_PLAIN).content("ana@example.com"))
                .andReturn();

        // Then
        assertProblem(result, 415, "UNSUPPORTED_MEDIA_TYPE", LOGIN);
    }

    static Stream<Arguments> requestIds() {
        return Stream.of(
                Arguments.of("rid-123", "rid-123"),
                Arguments.of("bad id!", null),
                Arguments.of(null, null));
    }

    @ParameterizedTest(name = "AC-1.7 X-Request-Id {0}")
    @MethodSource("requestIds")
    @Tag("AC-1.7")
    void aProblemCarriesTheCallersRequestIdOnlyWhenItIsValid(String sent, String expectedRequestId) throws Exception {
        // Given
        MockHttpServletRequestBuilder request = post(REGISTER).contentType(MediaType.APPLICATION_JSON)
                .content(ANA_REGISTRATION);
        if (sent != null) {
            request.header("X-Request-Id", sent);
        }

        // When
        MvcResult result = mockMvc.perform(request).andReturn();

        // Then
        DocumentContext body = assertProblem(result, 409, "EMAIL_ALREADY_REGISTERED", REGISTER);
        Map<String, Object> members = body.read("$");
        if (expectedRequestId == null) {
            assertThat(members).doesNotContainKey("requestId");
        } else {
            assertThat(members).containsEntry("requestId", expectedRequestId);
        }
        assertThat(result.getResponse().getHeaderNames()).doesNotContain("X-Request-Id");
    }

    @Test
    @Tag("AC-1.8")
    @DisplayName("AC-1.8 auth-service never emits CORS headers")
    void authServiceNeverEmitsCorsHeaders() throws Exception {
        // When
        MvcResult result = mockMvc.perform(post(LOGIN).header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("ana@example.com", AcmeFixture.PASSWORD)))
                .andReturn();

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getHeaderNames())
                .noneSatisfy(name -> assertThat(name).startsWithIgnoringCase("Access-Control-"));
    }
}
