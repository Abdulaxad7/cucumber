package org.apigateway.qa;

import org.apigateway.support.GatewayIntegrationTest;
import org.apigateway.support.StubResponse;
import org.apigateway.support.TestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QA leakage checks (D6, D7, NFR security): no secret, token, Authorization value, query string or invitation token
 * in gateway logs, and no token, query string or downstream host/port in gateway-generated error bodies.
 */
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("QA: secrets and internals never leak into logs or error bodies")
class QaLeakageTest extends GatewayIntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    @Tag("AC-7.5")
    @DisplayName("AC-7.5: tokens, Authorization values, passwords, query strings and invitation tokens are not logged")
    void credentialsNeverAppearInLogs(CapturedOutput output) {
        // Given
        String validToken = TestTokens.valid();
        String expiredToken = TestTokens.expired();
        String forgedToken = TestTokens.withAlteredSubject(TestTokens.FORGED_USER_ID);

        // When: protected with a valid, an expired and a forged token, public with a token, a password body,
        // query-string secrets and the canonical invitation path
        gateway.send(gateway.request("/api/orgs").header("Authorization", "Bearer " + validToken).POST(BodyPublishers.noBody()));
        gateway.send(gateway.request("/api/orgs").header("Authorization", "Bearer " + expiredToken).POST(BodyPublishers.noBody()));
        gateway.send(gateway.request("/api/orgs").header("Authorization", "Bearer " + forgedToken).POST(BodyPublishers.noBody()));
        gateway.send(gateway.request("/api/auth/login").header("Authorization", "Bearer " + validToken)
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString("{\"email\":\"ana@example.com\",\"password\":\"QaSecretPassw0rd!\"}")));
        gateway.send(gateway.request("/api/scenarios/42?apiKey=qa-query-secret&token=qa-query-token")
                .header("Authorization", "Bearer " + validToken).GET());
        gateway.send(gateway.request("/api/projects/1?apiKey=qa-query-secret-404")
                .header("Authorization", "Bearer " + validToken).GET());
        gateway.send(gateway.request("/api/auth/invitations/inv-QaCanonicalSecret/accept").POST(BodyPublishers.noBody()));
        gateway.send(gateway.request("/api/auth/invitations/inv-QaWrongMethodSecret/accept").GET());

        // Then: no log line carries any of these values
        List<String> secrets = List.of(validToken, expiredToken, forgedToken, signatureOf(validToken), TestTokens.SECRET,
                "QaSecretPassw0rd!", "qa-query-secret", "qa-query-token", "inv-QaCanonicalSecret", "inv-QaWrongMethodSecret",
                "authorization", "bearer ey");
        assertThat(output.getAll().lines()
                .filter(line -> secrets.stream().anyMatch(secret -> line.toLowerCase().contains(secret.toLowerCase())))
                .toList())
                .as("log lines containing a token, an Authorization value, a password, a query value or an invitation token")
                .isEmpty();
    }

    /**
     * BUG-api-gateway-2 (variant of AC-7.5). The invitation token is masked only when the raw path literally contains
     * {@code /api/auth/invitations/<token>}. A percent-encoded path that the gateway (and auth-service) still treat as
     * the public accept operation, or a path rejected by the firewall, is logged with the token in clear text.
     */
    @ParameterizedTest(name = "{1} {2}")
    @CsvSource({
            "1, POST, /api/auth/%69nvitations/inv-QaEncodedSecret1/accept, inv-QaEncodedSecret1",
            "2, POST, /api/%61uth/invitations/inv-QaEncodedSecret2/accept, inv-QaEncodedSecret2",
            "3, POST, /api/auth/inv%69tations/inv-QaEncodedSecret3/accept, inv-QaEncodedSecret3",
            "4, POST, /api/auth//invitations/inv-QaRejectedSecret4/accept, inv-QaRejectedSecret4",
            "5, POST, /api/auth/invitations/./inv-QaRejectedSecret5/accept, inv-QaRejectedSecret5"
    })
    @Tag("BUG-api-gateway-2")
    @DisplayName("BUG-api-gateway-2 (D7, variant of AC-7.5): invitation tokens in encoded or non-canonical paths are not logged")
    void invitationTokensInNonCanonicalPathsAreNotLogged(int id, String method, String path, String secret,
                                                         CapturedOutput output) {
        // Given a request ID that does not itself contain the token
        String requestId = "qa-mask-" + id;

        // When
        RawHttp.send(port, method, path, "X-Request-Id: " + requestId);

        // Then: the request was logged (one access entry), but no log line carries the token
        QaSupport.onlyAccessEntry(output, requestId);
        assertThat(output.getAll().lines().filter(line -> line.contains(secret)).toList())
                .as("log lines containing the invitation token %s", secret)
                .isEmpty();
    }

    /**
     * AC-7.7 (more rows, fix round): dot segments, encoded dot segments and path parameters around the invitation
     * path. Each path is sent raw, exactly as written. The firewall rejects all of them with 400 (they contain
     * {@code ..}, {@code %2e} or {@code ;}), and the access entry must still mask the token. The paths whose token
     * segment no server resolves as an invitation token ({@code ..;}, {@code %3B}, double encoding) are not listed
     * here; see the QA report (re-verification, BUG-api-gateway-15).
     */
    @ParameterizedTest(name = "mask-qa-{0}: POST {1}")
    @CsvSource({
            "1,  /api/auth/x/../invitations/inv-QaDot1Secret/accept,                   inv-QaDot1Secret",
            "2,  /api/auth/invitations/inv-QaDot2Secret/../inv-QaDot2bSecret/accept,   inv-QaDot2",
            "3,  /api/auth/invitations/%2e%2e/invitations/inv-QaDot3Secret/accept,     inv-QaDot3Secret",
            "4,  /api/auth/invitations/%2E/inv-QaDot4Secret/accept,                    inv-QaDot4Secret",
            "5,  /api/auth/invitations/.%2e/invitations/inv-QaDot5Secret/accept,       inv-QaDot5Secret",
            "6,  /api/auth/invitations;v=1/inv-QaSemi1Secret/accept,                   inv-QaSemi1Secret",
            "7,  /api/auth/invitations/inv-QaSemi2Secret;jsessionid=abc/accept,        inv-QaSemi2Secret",
            "8,  /api/auth;x=1/invitations/inv-QaSemi3Secret/accept,                   inv-QaSemi3Secret",
            "9,  /api;x=1/auth/invitations/inv-QaSemi4Secret/accept,                   inv-QaSemi4Secret",
            "10, /api/auth/invitations/;/inv-QaSemi5Secret/accept,                     inv-QaSemi5Secret",
            "11, /api/auth/invitations/inv-QaSemi6Secret;/accept,                      inv-QaSemi6Secret",
            "12, /api/auth/invitations/inv-QaDotSemi7Secret/..;/accept,                inv-QaDotSemi7Secret"
    })
    @Tag("AC-7.7")
    @DisplayName("AC-7.7 (more rows): invitation tokens next to dot segments or path parameters are masked in the access entry and never logged")
    void invitationTokensNextToDotSegmentsOrPathParametersAreMasked(int n, String path, String secret,
                                                                    CapturedOutput output) {
        // Given
        String requestId = "mask-qa-" + n;

        // When
        RawHttp.Response response = RawHttp.send(port, "POST", path, "X-Request-Id: " + requestId);

        // Then
        assertThat(response.status()).as("the request firewall rejects the non-canonical path").isEqualTo(400);
        String entry = QaSupport.onlyAccessEntry(output, requestId);
        assertThat(entry).contains(" status=400 ").containsPattern(" path=\\S*\\*\\*\\*\\S* ");
        assertThat(output.getAll().lines().filter(line -> line.contains(secret)).toList())
                .as("log lines containing the invitation token %s", secret)
                .isEmpty();
        assertNoStubReceivedARequest();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "401 no token,              POST, /api/orgs?token=qa-q-401",
            "401 expired token,         POST, /api/orgs?token=qa-q-401b",
            "404 valid token,           GET,  /api/projects/1?token=qa-q-404",
            "403 disallowed origin,     POST, /api/auth/login?token=qa-q-403",
            "502 dropped connection,    GET,  /api/scenarios/42?token=qa-q-502"
    })
    @Tag("AC-5.2")
    @DisplayName("AC-5.2: gateway error bodies contain no token, query string, downstream host or port, or stack trace")
    void errorBodiesRevealNothingInternal(String situation, String method, String pathAndQuery) {
        // Given
        SCENARIO_SERVICE.respondWith(StubResponse.dropConnection());
        String token = situation.startsWith("401 expired") ? TestTokens.expired() : TestTokens.valid();
        var request = gateway.request(pathAndQuery).method(method, BodyPublishers.noBody());
        if (!situation.startsWith("401 no token")) {
            request.header("Authorization", "Bearer " + token);
        }
        if (situation.startsWith("403")) {
            request.header("Origin", "https://evil.example");
        }

        // When
        HttpResponse<String> response = gateway.send(request);

        // Then
        assertThat(response.statusCode()).isEqualTo(Integer.parseInt(situation.substring(0, 3)));
        List<String> internals = List.of(token, signatureOf(token), "token=", "qa-q-", "localhost",
                String.valueOf(AUTH_SERVICE.port()), String.valueOf(SCENARIO_SERVICE.port()), String.valueOf(REPORT_SERVICE.port()),
                "Exception", "at org.", "at java.", "trace", "127.0.0.1");
        assertThat(response.body()).doesNotContain(internals.toArray(String[]::new));
    }

    private static String signatureOf(String token) {
        return token.substring(token.lastIndexOf('.') + 1);
    }
}
