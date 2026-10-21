package org.apigateway.qa;

import org.apigateway.support.GatewayIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QA adversarial checks for the public-operation list (D2, D4, AC-2.6): only the three exact method + path pairs
 * pass without a token. Requests are sent over a raw socket so that paths reach the gateway exactly as written.
 */
@DisplayName("QA: public-operation bypass attempts")
class QaPublicOperationBypassTest extends GatewayIntegrationTest {

    @LocalServerPort
    private int port;

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "GET,    /api/auth/login",
            "PUT,    /api/auth/login",
            "PATCH,  /api/auth/login",
            "DELETE, /api/auth/register",
            "GET,    /api/auth/register",
            "PUT,    /api/auth/invitations/inv-7Kq2/accept",
            "GET,    /api/auth/invitations/inv-7Kq2/accept",
            "HEAD,   /api/auth/login"
    })
    @Tag("AC-2.6")
    @DisplayName("AC-2.6: the wrong method on a public path needs a token")
    void wrongMethodOnAPublicPathIsProtected(String method, String path) {
        // When
        RawHttp.Response response = RawHttp.send(port, method, path);

        // Then
        assertThat(response.status()).isEqualTo(401);
        assertNoStubReceivedARequest();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "/api/auth/login/",
            "/api/auth/login.json",
            "/API/AUTH/LOGIN",
            "/api/Auth/login",
            "/api/auth/LOGIN",
            "/api/auth/register/x",
            "/api/auth/invitations/x/accept/extra",
            "/api/auth/invitations/x/accept/",
            "/api/auth/invitations/x/y/accept",
            "/api/auth/invitations/x/ACCEPT",
            "/api/auth/invitations/accept",
            "/api/auth/login%20"
    })
    @Tag("AC-2.6")
    @DisplayName("AC-2.6: near-miss paths of public operations (suffix, trailing slash, case) need a token")
    void nearMissPathsAreProtected(String path) {
        // When
        RawHttp.Response response = RawHttp.send(port, "POST", path);

        // Then
        assertThat(response.status()).isEqualTo(401);
        assertNoStubReceivedARequest();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "//api/orgs",
            "/api/auth/login/../../orgs",
            "/api/auth/login/%2e%2e/%2e%2e/orgs",
            "/api/auth/login/%2E%2E/%2E%2E/orgs",
            "/api/auth/login%2F..%2F..%2Forgs",
            "/api/auth/login%2f%2e%2e%2f%2e%2e%2forgs",
            "/api/auth/login;jsessionid=abc",
            "/api/auth/login;x=y/../../orgs",
            "/api/auth/register/..;/..;/orgs",
            "/api/auth/invitations/..%2F..%2F..%2Forgs/accept",
            "/api/auth/invitations/%2e%2e/%2e%2e/%2e%2e/orgs/accept",
            "/api/auth/./login",
            "/api//auth/login",
            "/api/auth/login/.",
            "/api/auth/login%00",
            "/api/auth/login\\..\\..\\orgs",
            "/api/auth/login%5C..%5C..%5Corgs"
    })
    @Tag("AC-2.6")
    @DisplayName("AC-2.6: traversal, encoded-slash and matrix-parameter tricks never reach a service without a token")
    void normalisationTricksNeverReachAService(String path) {
        // When
        RawHttp.Response response = RawHttp.send(port, "POST", path);

        // Then: rejected (the exact 4xx is not specified; see the QA report) and nothing forwarded
        assertThat(response.status()).as("status for %s", path).isIn(400, 401);
        assertNoStubReceivedARequest();
    }

    @Test
    @Tag("AC-2.6")
    @DisplayName("AC-2.6: a public path with a protected path in its query string is forwarded only as that public path")
    void aProtectedPathInTheQueryStringDoesNotChangeTheTarget() {
        // When
        RawHttp.Response response = RawHttp.send(port, "POST", "/api/auth/login?next=/api/orgs");

        // Then
        assertThat(response.status()).isEqualTo(200);
        assertThat(AUTH_SERVICE.requests()).singleElement()
                .satisfies(request -> assertThat(request.path()).isEqualTo("/api/auth/login"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/api/orgs", "/api/orgs/", "/api/orgs/3b9d5c1e-0000-4000-8000-0000000000aa/invitations",
            "/api/scenarios/42", "/api/reports/42", "/api/authx/login", "/api/auth", "/api/auth/"})
    @Tag("AC-2.3")
    @DisplayName("AC-2.3 / A9: protected and unknown paths without a token get 401 and reach no service")
    void protectedPathsWithoutATokenAre401(String path) {
        // When
        RawHttp.Response response = RawHttp.send(port, "POST", path);

        // Then
        assertThat(response.status()).isEqualTo(401);
        assertNoStubReceivedARequest();
    }
}
