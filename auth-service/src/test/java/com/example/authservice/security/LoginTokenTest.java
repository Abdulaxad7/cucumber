package com.example.authservice.security;

import com.example.authservice.enums.UserStatus;
import com.example.authservice.support.AcmeFixture;
import com.example.authservice.support.AcmeFixture.Acme;
import com.example.authservice.support.AuthServiceIntegrationTest;
import com.example.authservice.support.TestJwt;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.example.authservice.support.ApiCalls.bearer;
import static com.example.authservice.support.ApiCalls.loginBody;
import static com.example.authservice.support.ApiCalls.tokenOf;
import static com.example.authservice.support.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * US-2 login behaviour pinned by T3: the issued token, user status and bad tokens on a public operation.
 */
@AuthServiceIntegrationTest
class LoginTokenTest {

    private static final String LOGIN = "/api/auth/login";
    private static final long ONE_DAY_SECONDS = 86_400;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AcmeFixture fixture;

    private Acme acme;

    @BeforeEach
    void seedAcme() {
        acme = fixture.seed();
    }

    @Test
    @Tag("AC-2.3")
    @DisplayName("AC-2.3 Login returns an HS512 token with the agreed claims")
    void loginReturnsAnHs512TokenWithTheAgreedClaims() throws Exception {
        // When
        String token = tokenOf(mockMvc, "ana@example.com");

        // Then
        Jws<Claims> jws = Jwts.parser().setSigningKey(TestJwt.key()).build().parseClaimsJws(token);
        assertThat(jws.getHeader().getAlgorithm()).isEqualTo("HS512");
        Claims claims = jws.getBody();
        assertThat(claims.getSubject()).isEqualTo(acme.ana().getId().toString());
        assertThat(claims.get("email", String.class)).isEqualTo("ana@example.com");
        assertThat(claims.get("mfa", Boolean.class)).isFalse();
        assertThat(claims.get("orgs", List.class))
                .containsExactly(Map.of("orgId", acme.orgId().toString(), "role", "OWNER"));
        long lifetimeSeconds = (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;
        assertThat(lifetimeSeconds).isEqualTo(ONE_DAY_SECONDS);
    }

    @Test
    @Tag("AC-2.6")
    @DisplayName("AC-2.6 A suspended user can't log in and gets the same answer as a wrong password")
    void aSuspendedUserCantLogIn() throws Exception {
        // Given
        fixture.setStatus(acme.cai(), UserStatus.SUSPENDED);

        // When
        MvcResult suspended = login("cai@example.com", AcmeFixture.PASSWORD, null);
        MvcResult wrongPassword = login("ana@example.com", "Wrong123!", null);

        // Then
        String suspendedDetail = assertProblem(suspended, 401, "INVALID_CREDENTIALS", LOGIN)
                .read("$.detail", String.class);
        String wrongPasswordDetail = assertProblem(wrongPassword, 401, "INVALID_CREDENTIALS", LOGIN)
                .read("$.detail", String.class);
        assertThat(suspendedDetail).isEqualTo(wrongPasswordDetail);
    }

    @Test
    @Tag("AC-2.7")
    @DisplayName("AC-2.7 A pending user can log in in phase 1")
    void aPendingUserCanLogIn() throws Exception {
        // Given Eve's status is PENDING_VERIFICATION (fixture)
        assertThat(acme.eve().getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);

        // When
        String token = tokenOf(mockMvc, "eve@example.com");

        // Then
        Claims claims = Jwts.parser().setSigningKey(TestJwt.key()).build().parseClaimsJws(token).getBody();
        assertThat(claims.get("orgs", List.class)).isEmpty();
    }

    static Stream<Arguments> badTokens() {
        return Stream.of(
                Arguments.of("Bearer not-a-jwt", (Function<Acme, String>) acme -> "Bearer not-a-jwt"),
                Arguments.of("Bearer {Ana's expired token}", (Function<Acme, String>) acme ->
                        bearer(TestJwt.expired(acme.ana().getId(), "ana@example.com"))));
    }

    @ParameterizedTest(name = "AC-2.8 {0}")
    @MethodSource("badTokens")
    @Tag("AC-2.8")
    void aBadTokenOnAPublicOperationIsIgnored(String row, Function<Acme, String> authorization) throws Exception {
        // When
        MvcResult result = login("ana@example.com", AcmeFixture.PASSWORD, authorization.apply(acme));

        // Then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    private MvcResult login(String email, String password, String authorization) throws Exception {
        var request = post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(loginBody(email, password));
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return mockMvc.perform(request).andReturn();
    }
}
