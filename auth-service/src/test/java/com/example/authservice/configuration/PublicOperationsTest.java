package com.example.authservice.configuration;

import com.example.authservice.support.AcmeFixture;
import com.example.authservice.support.AuthServiceIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static com.example.authservice.support.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * D7: exactly three auth operations are public, plus the API docs on the service port.
 */
@AuthServiceIntegrationTest
class PublicOperationsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AcmeFixture fixture;

    @BeforeEach
    void resetData() {
        fixture.reset();
    }

    @ParameterizedTest(name = "AC-3.6 anonymous {0} {1}")
    @CsvSource({
            "GET,  /api/orgs",
            "POST, /api/orgs",
            "GET,  /api/auth/login",
            "POST, /api/auth/logout",
            "GET,  /api/auth/invitations/inv-7Kq2/accept",
            "POST, /api/v1/auth/login",
            "GET,  /h2-console",
            "GET,  /actuator/health"
    })
    @Tag("AC-3.6")
    void onlyTheThreeAuthOperationsArePublic(String method, String path) throws Exception {
        // When
        MvcResult result = mockMvc.perform(request(HttpMethod.valueOf(method), path))
                .andReturn();

        // Then
        assertProblem(result, 401, "UNAUTHENTICATED", path);
        assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
    }

    @Test
    @Tag("AC-3.7")
    @DisplayName("AC-3.7 The API docs stay public on the service port")
    void theApiDocsStayPublic() throws Exception {
        // When
        MvcResult result = mockMvc.perform(get("/v3/api-docs")).andReturn();

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }
}
