package org.apigateway.filter;

import org.apigateway.support.GatewayIntegrationTest;
import org.apigateway.support.TestTokens;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.http.HttpRequest.BodyPublishers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Caller identity passed downstream safely (D4, US-3).
 */
class IdentityHeaderFilterTest extends GatewayIntegrationTest {

    @Test
    @Tag("AC-3.1")
    void theGatewayTellsTheServiceWhichUserMadeTheRequest() {
        // When
        gateway.sendWithValidToken("POST", "/api/orgs");

        // Then
        assertThat(AUTH_SERVICE.requests()).singleElement().satisfies(request ->
                assertThat(request.headerValues("X-User-Id")).containsExactly(TestTokens.USER_ID));
    }

    @Test
    @Tag("AC-3.2")
    void aForgedIdentityHeaderOnAProtectedRequestIsReplaced() {
        // When
        gateway.send(gateway.request("/api/orgs")
                .header("Authorization", TestTokens.bearerValid())
                .header("X-User-Id", TestTokens.FORGED_USER_ID)
                .POST(BodyPublishers.noBody()));

        // Then
        assertThat(AUTH_SERVICE.requests()).singleElement().satisfies(request ->
                assertThat(request.headerValues("X-User-Id")).containsExactly(TestTokens.USER_ID));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"X-User-Id", "x-user-id"})
    @Tag("AC-3.3")
    void aForgedIdentityHeaderOnAPublicRequestIsRemoved(String headerName) {
        // When
        gateway.send(gateway.request("/api/auth/login")
                .header(headerName, TestTokens.FORGED_USER_ID)
                .POST(BodyPublishers.noBody()));

        // Then
        assertThat(AUTH_SERVICE.requests()).singleElement().satisfies(request ->
                assertThat(request.hasHeader("X-User-Id")).as("X-User-Id in any letter case").isFalse());
    }

    @Test
    void aPublicOperationGetsNoIdentityHeaderEvenWithAValidToken() {
        // When
        gateway.send(gateway.request("/api/auth/login")
                .header("Authorization", TestTokens.bearerValid())
                .POST(BodyPublishers.noBody()));

        // Then
        assertThat(AUTH_SERVICE.requests()).singleElement().satisfies(request ->
                assertThat(request.hasHeader("X-User-Id")).isFalse());
    }

    @Test
    void severalForgedIdentityHeadersOnAProtectedRequestLeaveExactlyOne() {
        // When
        gateway.send(gateway.request("/api/orgs")
                .header("Authorization", TestTokens.bearerValid())
                .header("X-User-Id", TestTokens.FORGED_USER_ID)
                .header("x-user-id", "another-forged-value")
                .POST(BodyPublishers.noBody()));

        // Then
        assertThat(AUTH_SERVICE.requests()).singleElement().satisfies(request ->
                assertThat(request.headerValues("X-User-Id")).containsExactly(TestTokens.USER_ID));
    }
}
