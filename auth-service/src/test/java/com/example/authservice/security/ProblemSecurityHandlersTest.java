package com.example.authservice.security;

import com.example.authservice.support.AcmeFixture;
import com.example.authservice.support.AuthServiceIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static com.example.authservice.support.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The security chain's 401 entry point and 403 handler answer with Problems (E2).
 */
@AuthServiceIntegrationTest
class ProblemSecurityHandlersTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AcmeFixture fixture;

    @Autowired
    private ProblemAccessDeniedHandler accessDeniedHandler;

    @BeforeEach
    void resetData() {
        fixture.reset();
    }

    @Test
    void anAnonymousRequestToAProtectedPathIsA401ProblemWithABearerChallenge() throws Exception {
        // When
        MvcResult result = mockMvc.perform(post("/api/orgs").header("X-Request-Id", "rid-401")).andReturn();

        // Then
        assertThat(assertProblem(result, 401, "UNAUTHENTICATED", "/api/orgs").read("$.requestId", String.class))
                .isEqualTo("rid-401");
        assertThat(result.getResponse().getHeaders(HttpHeaders.WWW_AUTHENTICATE)).containsExactly("Bearer");
    }

    @Test
    void theAccessDeniedHandlerAnswersWithA403Problem() throws Exception {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orgs/anything");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // When
        accessDeniedHandler.handle(request, response, new AccessDeniedException("internal reason"));

        // Then
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        assertThat(response.getContentAsString())
                .contains("\"code\":\"FORBIDDEN\"", "\"instance\":\"/api/orgs/anything\"")
                .doesNotContain("internal reason");
    }
}
