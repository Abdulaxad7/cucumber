package org.apigateway;

import org.apigateway.support.GatewayIntegrationTest;
import org.apigateway.support.StubResponse;
import org.apigateway.support.StubServer;
import org.apigateway.support.TestTokens;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The routing table in application.yaml (D2, D3): each prefix reaches its owning service unchanged.
 */
class RoutingTest extends GatewayIntegrationTest {

    private static final String LOGIN_BODY = "{\"email\":\"ana@example.com\",\"password\":\"Secret123!\"}";

    @Autowired
    private Environment environment;

    @Test
    @Tag("AC-1.1")
    void loginIsForwardedToAuthServiceAndItsResponseIsReturnedUnchanged() {
        // Given
        AUTH_SERVICE.respondWith(StubResponse.json(200, "{\"accessToken\":\"stub-token\"}"));

        // When
        HttpResponse<String> response = gateway.send(gateway.request("/api/auth/login")
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(LOGIN_BODY)));

        // Then
        assertThat(AUTH_SERVICE.requests()).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.path()).isEqualTo("/api/auth/login");
            assertThat(request.body()).isEqualTo(LOGIN_BODY);
        });
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/json");
        assertThat(response.body()).isEqualTo("{\"accessToken\":\"stub-token\"}");
    }

    @ParameterizedTest(name = "{0} {1} -> {2}")
    @CsvSource({
            "POST, /api/auth/register,                                         auth-service",
            "POST, /api/auth/invitations/inv-7Kq2/accept,                      auth-service",
            "POST, /api/orgs,                                                  auth-service",
            "POST, /api/orgs/3b9d5c1e-0000-4000-8000-0000000000aa/invitations, auth-service",
            "GET,  /api/scenarios/42,                                          scenario-service",
            "GET,  /api/reports/42,                                            report-service"
    })
    @Tag("AC-1.2")
    void eachPathPrefixIsForwardedToItsOwningService(String method, String path, String service) {
        // Given
        StubServer owner = stub(service);

        // When
        gateway.sendWithValidToken(method, path);

        // Then
        assertThat(owner.requests()).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo(method);
            assertThat(request.path()).isEqualTo(path);
        });
        stubsOtherThan(owner).forEach(other -> assertThat(other.requests()).as(other.name()).isEmpty());
    }

    @Test
    @Tag("AC-1.3")
    void methodPathQueryHeadersAndBodyAreForwardedWithoutRewriting() {
        // When
        gateway.send(gateway.request("/api/orgs/3b9d5c1e-0000-4000-8000-0000000000aa?notify=false&page=2")
                .header("Authorization", TestTokens.bearerValid())
                .header("Accept-Language", "pt-BR")
                .header("Content-Type", "application/json")
                .method("PATCH", BodyPublishers.ofString("{\"name\":\"Acme QA\"}")));

        // Then
        assertThat(AUTH_SERVICE.requests()).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("PATCH");
            assertThat(request.path()).isEqualTo("/api/orgs/3b9d5c1e-0000-4000-8000-0000000000aa");
            assertThat(request.query()).isEqualTo("notify=false&page=2");
            assertThat(request.header("Accept-Language")).hasValue("pt-BR");
            assertThat(request.body()).isEqualTo("{\"name\":\"Acme QA\"}");
        });
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource(delimiter = '|', value = {
            "PATCH  | name=Acme+QA",
            "PUT    | name=Acme+QA",
            "DELETE | name=Acme+QA",
            "PATCH  | {\"name\":\"Acme QA\"}"
    })
    @Tag("AC-1.7")
    void formEncodedBodiesStayInTheBodyAndNeverMoveIntoTheUrl(String method, String body) {
        // When
        gateway.send(gateway.request("/api/orgs/3b9d5c1e-0000-4000-8000-0000000000aa?notify=false&page=2")
                .header("Authorization", TestTokens.bearerValid())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .method(method, BodyPublishers.ofString(body)));

        // Then
        assertThat(AUTH_SERVICE.requests()).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo(method);
            assertThat(request.path()).isEqualTo("/api/orgs/3b9d5c1e-0000-4000-8000-0000000000aa");
            assertThat(request.query()).isEqualTo("notify=false&page=2");
            assertThat(request.body()).isEqualTo(body);
        });
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "400 | {\"message\":\"Password must be at least 8 characters long\"}",
            "403 | {\"message\":\"Forbidden\"}",
            "409 | {\"message\":\"Email already exists: ana@example.com\"}",
            "500 | {\"message\":\"Internal error\"}"
    })
    @Tag("AC-1.4")
    void errorResponsesFromAServiceArePassedThroughUnchanged(int status, String body) {
        // Given
        AUTH_SERVICE.respondWith(StubResponse.json(status, body));

        // When
        HttpResponse<String> response = gateway.send("POST", "/api/auth/register");

        // Then
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/json");
        assertThat(response.body()).isEqualTo(body);
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "GET,  /api/projects/1",
            "POST, /api/authx/login",
            "GET,  /api",
            "GET,  /h2-console",
            "GET,  /swagger-ui/index.html",
            "GET,  /v3/api-docs"
    })
    @Tag("AC-1.5")
    void aPathThatMatchesNoRouteReturns404AndReachesNoService(String method, String path) {
        // When
        HttpResponse<String> response = gateway.sendWithValidToken(method, path);

        // Then
        assertThat(response.statusCode()).isEqualTo(404);
        assertNoStubReceivedARequest();
    }

    @Test
    @Tag("AC-8.1")
    void serviceLocationsComeFromConfiguration() {
        // Given the auth-service stub listens on a random port P and AUTH_SERVICE_URL points at it
        assertThat(environment.getProperty("AUTH_SERVICE_URL")).isEqualTo("http://localhost:" + AUTH_SERVICE.port());

        // When
        gateway.send("POST", "/api/auth/login");

        // Then
        assertThat(AUTH_SERVICE.requests()).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.path()).isEqualTo("/api/auth/login");
        });
    }
}
