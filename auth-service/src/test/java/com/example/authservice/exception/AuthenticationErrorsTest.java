package com.example.authservice.exception;

import com.example.authservice.repository.UserRepository;
import com.example.authservice.support.AcmeFixture;
import com.example.authservice.support.AuthServiceIntegrationTest;
import com.jayway.jsonpath.DocumentContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static com.example.authservice.support.ApiCalls.loginBody;
import static com.example.authservice.support.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * US-2 error cases owned by the error format (T2): wrong credentials and a duplicate registration.
 */
@AuthServiceIntegrationTest
class AuthenticationErrorsTest {

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

    @Test
    @Tag("AC-2.4")
    @DisplayName("AC-2.4 Wrong credentials give the same 401 Problem whatever was wrong")
    void wrongCredentialsGiveTheSame401ProblemWhateverWasWrong() throws Exception {
        // When
        MvcResult wrongPassword = login("ana@example.com", "Wrong123!");
        MvcResult unknownEmail = login("nobody@example.com", AcmeFixture.PASSWORD);

        // Then
        DocumentContext first = assertProblem(wrongPassword, 401, "INVALID_CREDENTIALS", "/api/auth/login");
        DocumentContext second = assertProblem(unknownEmail, 401, "INVALID_CREDENTIALS", "/api/auth/login");
        assertThat(second.read("$.detail", String.class)).isEqualTo(first.read("$.detail", String.class));
    }

    @Test
    @Tag("AC-2.2")
    @DisplayName("AC-2.2 An already registered email is a conflict (row ana@example.com only; "
            + "row Ana@Example.COM needs email normalisation, added in T10)")
    void anAlreadyRegisteredEmailIsAConflict() throws Exception {
        // When
        MvcResult result = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@example.com\",\"password\":\"Secret123!\",\"fullName\":\"Someone\"}"))
                .andReturn();

        // Then
        assertProblem(result, 409, "EMAIL_ALREADY_REGISTERED", "/api/auth/register");
        assertThat(userRepository.findAll()).filteredOn(user -> user.getEmail().equals("ana@example.com")).hasSize(1);
    }

    private MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, password))).andReturn();
    }
}
