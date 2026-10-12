package org.apigateway.qa;

import org.apigateway.support.GatewayIntegrationTest;
import org.apigateway.support.TestTokens;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.apigateway.qa.QaSupport.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * QA re-verification of the fix round (T15, T17): downstream failures after the response headers arrive, invalid
 * status lines and response framing, the headers of the resulting Problem response, state isolation between
 * concurrent requests, and a raw lower-case downstream {@code x-request-id} (AC-7.6). auth-service and
 * scenario-service are raw socket stubs; every scripted reply carries {@code Connection: close} so the gateway never
 * reuses a connection the stub has closed.
 */
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {"GATEWAY_RESPONSE_TIMEOUT=2s", "CORS_ALLOWED_ORIGINS=" + QaDownstreamResponseFailureTest.ALLOWED_ORIGIN})
@DisplayName("QA: failures after the response headers arrive, and downstream request IDs (fix round)")
class QaDownstreamResponseFailureTest extends GatewayIntegrationTest {

    static final String ALLOWED_ORIGIN = "http://localhost:5173";

    private static final RawStubServer RAW_AUTH_SERVICE = RawStubServer.start();
    private static final RawStubServer RAW_SCENARIO_SERVICE = RawStubServer.start();
    private static final String UNEXPECTED_GATEWAY_ERROR = "Unexpected gateway error";

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void authAndScenarioServicesAreRawStubs(DynamicPropertyRegistry registry) {
        registry.add("gateway.downstream.auth-service-url", RAW_AUTH_SERVICE::baseUrl);
        registry.add("gateway.downstream.scenario-service-url", RAW_SCENARIO_SERVICE::baseUrl);
    }

    @BeforeEach
    void resetRawStubs() {
        RAW_AUTH_SERVICE.reset();
        RAW_SCENARIO_SERVICE.reset();
    }

    @AfterAll
    static void stopRawStubs() {
        RAW_AUTH_SERVICE.close();
        RAW_SCENARIO_SERVICE.close();
    }

    // ---- AC-7.6: exactly one X-Request-Id, whatever letter case the service uses --------------------------------

    @Test
    @Tag("AC-7.6")
    @DisplayName("AC-7.6: raw lower-case and upper-case X-Request-Id headers from the service are dropped; the client gets exactly one, its own")
    void rawDownstreamRequestIdsInAnyLetterCaseAreDropped() {
        // Given auth-service answers with a raw lower-case x-request-id (byte for byte), an upper-case copy, and a
        // look-alike header that must survive
        RAW_AUTH_SERVICE.on("/api/auth/login", out -> out.write(RawStubServer.ascii("HTTP/1.1 200 OK\r\n"
                + "Content-Type: application/json\r\nx-request-id: downstream-echo\r\nX-REQUEST-ID: downstream-upper\r\n"
                + "X-Request-Id-Echo: kept\r\nContent-Length: 2\r\nConnection: close\r\n\r\n{}")));

        // When the client sends its own request ID, then none
        RawHttp.Response withClientId = RawHttp.send(port, "POST", "/api/auth/login", "X-Request-Id: client-rid");
        RawHttp.Response withoutClientId = RawHttp.send(port, "POST", "/api/auth/login");

        // Then, on the wire, exactly one X-Request-Id in any letter case, with the gateway's value
        assertThat(withClientId.status()).isEqualTo(200);
        assertThat(withClientId.headerValues("X-Request-Id")).containsExactly("client-rid");
        assertThat(withClientId.headerValues("X-Request-Id-Echo")).containsExactly("kept");
        assertThat(withClientId.body()).isEqualTo("{}");

        assertThat(withoutClientId.headerValues("X-Request-Id")).singleElement().satisfies(requestId ->
                assertThat(requestId).matches(QaSupport.UUID_PATTERN));
        assertThat(RAW_AUTH_SERVICE.requestHeaderValues("X-Request-Id"))
                .containsExactly(List.of("client-rid"), withoutClientId.headerValues("X-Request-Id"));
    }

    // ---- AC-4.7: invalid status lines and broken framing --------------------------------------------------------

    @ParameterizedTest(name = "status line \"HTTP/1.1 {0} Weird\"")
    @ValueSource(strings = {"000", "099", "600", "1000", "abc"})
    @Tag("AC-4.7")
    @DisplayName("AC-4.7 (more rows): any status code outside 100-599, or no number at all, produces the 502 Problem, never 500")
    void statusCodesOutsideTheValidRangeAre502(String statusCode, CapturedOutput output) {
        // Given
        RAW_SCENARIO_SERVICE.on("/api/scenarios/42", out -> out.write(RawStubServer.ascii("HTTP/1.1 " + statusCode
                + " Weird\r\nContent-Type: application/json\r\nContent-Length: 2\r\nConnection: close\r\n\r\n{}")));

        // When
        HttpResponse<String> response = gateway.sendWithValidToken("GET", "/api/scenarios/42");

        // Then
        assertProblemResponse(response, 502, "Bad Gateway", "/api/scenarios/42");
        assertThat(output.getAll()).doesNotContain(UNEXPECTED_GATEWAY_ERROR);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"chunked body cut after the first chunk", "invalid chunk size"})
    @Tag("AC-4.7")
    @DisplayName("AC-4.7 (more rows): a chunked body that is cut off or malformed produces the 502 Problem")
    void brokenChunkedBodiesAre502(String reply, CapturedOutput output) {
        // Given
        String body = reply.startsWith("chunked body cut") ? "5\r\nhello\r\n" : "ZZ\r\nhello\r\n0\r\n\r\n";
        RAW_SCENARIO_SERVICE.on("/api/scenarios/42", out -> out.write(RawStubServer.ascii(
                "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n" + body)));

        // When
        HttpResponse<String> response = gateway.sendWithValidToken("GET", "/api/scenarios/42");

        // Then
        assertProblemResponse(response, 502, "Bad Gateway", "/api/scenarios/42");
        assertThat(output.getAll()).doesNotContain(UNEXPECTED_GATEWAY_ERROR);
    }

    @Test
    @Tag("AC-1.4")
    @DisplayName("T15 must-not (control): a complete, valid close-delimited HTTP/1.0 response is still passed through unchanged")
    void aValidCloseDelimitedResponseIsPassedThrough() {
        // Given a response whose body ends when the connection closes (valid for HTTP/1.0 without Content-Length)
        RAW_SCENARIO_SERVICE.on("/api/scenarios/42", out -> out.write(RawStubServer.ascii(
                "HTTP/1.0 200 OK\r\nContent-Type: text/plain\r\nX-Downstream: complete\r\n\r\nclose-delimited-body")));

        // When
        HttpResponse<String> response = gateway.sendWithValidToken("GET", "/api/scenarios/42");

        // Then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().allValues("X-Downstream")).containsExactly("complete");
        assertThat(response.body()).isEqualTo("close-delimited-body");
    }

    // ---- AC-4.6 / AC-4.7: the time-based 502/504 classification --------------------------------------------------

    @Test
    @Tag("AC-4.7")
    @DisplayName("AC-4.7 (timing boundary): a body cut off 1.5 s after the headers, inside the 2 s response timeout, is 502, not 504")
    void aBodyCutOffBeforeTheResponseTimeoutIs502() {
        // Given the headers and 11 of 100 bytes at once, then the connection closes 1.5 s later
        RAW_SCENARIO_SERVICE.on("/api/scenarios/42", out -> {
            out.write(RawStubServer.ascii("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 100\r\n"
                    + "Connection: close\r\n\r\n{\"partial\":"));
            out.flush();
            Thread.sleep(1500);
        });

        // When
        long start = System.nanoTime();
        HttpResponse<String> response = gateway.sendWithValidToken("GET", "/api/scenarios/42");
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        // Then
        assertProblemResponse(response, 502, "Bad Gateway", "/api/scenarios/42");
        assertThat(elapsed).as("the cut happened late, close to the timeout").isGreaterThanOrEqualTo(Duration.ofMillis(1400));
    }

    @Test
    @Tag("AC-4.6")
    @DisplayName("AC-4.6 (variant): a body that stalls after the headers, with Content-Length, is 504 within 4 s")
    void aStalledBodyWithAContentLengthIs504() {
        // Given the headers and 11 of 100 bytes, then nothing for 5 s
        RAW_SCENARIO_SERVICE.on("/api/scenarios/42", out -> {
            out.write(RawStubServer.ascii("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 100\r\n"
                    + "Connection: close\r\n\r\n{\"partial\":"));
            out.flush();
            Thread.sleep(5000);
        });

        // When
        long start = System.nanoTime();
        HttpResponse<String> response = gateway.sendWithValidToken("GET", "/api/scenarios/42");
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        // Then
        assertProblemResponse(response, 504, "Gateway Timeout", "/api/scenarios/42");
        assertThat(elapsed).isLessThan(Duration.ofSeconds(4));
    }

    // ---- The Problem response after the headers arrived ----------------------------------------------------------

    @Test
    @Tag("AC-4.7")
    @Tag("AC-6.4")
    @DisplayName("AC-4.7 / AC-6.4: a 502 after the headers arrived carries no header of the failed response, correct framing, one request ID and the CORS grant")
    void theProblemAfterHeadersCarriesOnlyTheGatewaysHeaders() {
        // Given a response with a cookie, a cache directive, a custom header and Content-Length 100, cut after 11 bytes
        RAW_SCENARIO_SERVICE.on("/api/scenarios/42", out -> out.write(RawStubServer.ascii("HTTP/1.1 200 OK\r\n"
                + "Content-Type: application/json\r\nContent-Length: 100\r\nSet-Cookie: session=downstream\r\n"
                + "Cache-Control: max-age=60\r\nX-Downstream: partial\r\nAccess-Control-Allow-Origin: *\r\n"
                + "Connection: close\r\n\r\n{\"partial\":")));

        // When
        RawHttp.Response response = RawHttp.send(port, "GET", "/api/scenarios/42",
                "Authorization: " + TestTokens.bearerValid(), "Origin: " + ALLOWED_ORIGIN, "X-Request-Id: qa-after-headers");

        // Then
        assertProblem(response.status(), response.headerValues("Content-Type").get(0),
                response.headerValues("X-Request-Id").get(0), response.body(), 502, "Bad Gateway", "/api/scenarios/42");
        assertThat(response.headerValues("X-Request-Id")).containsExactly("qa-after-headers");
        assertThat(response.headerValues("Set-Cookie")).isEmpty();
        assertThat(response.headerValues("X-Downstream")).isEmpty();
        assertThat(response.headerValues("Cache-Control")).doesNotContain("max-age=60");
        assertThat(response.headerValues("Access-Control-Allow-Origin")).containsExactly(ALLOWED_ORIGIN);
        List<String> contentLength = response.headerValues("Content-Length");
        if (!contentLength.isEmpty()) {
            assertThat(contentLength).as("Content-Length of the Problem, not of the failed response")
                    .containsExactly(String.valueOf(response.body().getBytes(StandardCharsets.UTF_8).length));
        }
    }

    @Test
    @Tag("AC-6.4")
    @DisplayName("AC-6.4: a 504 after the headers arrived (slow body) carries the CORS grant for an allowed origin")
    void theTimeoutProblemAfterHeadersCarriesTheCorsGrant() {
        // Given
        RAW_SCENARIO_SERVICE.on("/api/scenarios/42", out -> {
            out.write(RawStubServer.ascii("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nTransfer-Encoding: chunked\r\n"
                    + "Connection: close\r\n\r\n"));
            for (int part = 1; part <= 4; part++) {
                out.write(RawStubServer.ascii("6\r\npart-" + part + "\r\n"));
                out.flush();
                Thread.sleep(1000);
            }
        });

        // When
        RawHttp.Response response = RawHttp.send(port, "GET", "/api/scenarios/42",
                "Authorization: " + TestTokens.bearerValid(), "Origin: " + ALLOWED_ORIGIN);

        // Then
        assertThat(response.status()).isEqualTo(504);
        assertThat(response.headerValues("Access-Control-Allow-Origin")).containsExactly(ALLOWED_ORIGIN);
        assertThat(response.headerValues("X-Request-Id")).hasSize(1);
        assertThat(response.body()).doesNotContain("part-");
    }

    // ---- State isolation (request attributes, RequestContextHolder, response reset) ----------------------------

    @Test
    @Tag("AC-4.7")
    @DisplayName("T15 thread safety: 60 concurrent requests mixing cut bodies, invalid status lines and good responses each get their own correct answer")
    void concurrentFailuresAndSuccessesDoNotLeakIntoEachOther(CapturedOutput output) {
        // Given three behaviours on the same service
        RAW_SCENARIO_SERVICE.on("/api/scenarios/cut", out -> out.write(RawStubServer.ascii("HTTP/1.1 200 OK\r\n"
                + "Content-Type: application/json\r\nContent-Length: 100\r\nX-Downstream: cut\r\nConnection: close\r\n\r\n{\"p\":")));
        RAW_SCENARIO_SERVICE.on("/api/scenarios/invalid", out -> out.write(RawStubServer.ascii(
                "HTTP/1.1 999 Weird\r\nX-Downstream: invalid\r\nContent-Length: 2\r\nConnection: close\r\n\r\n{}")));
        RAW_SCENARIO_SERVICE.on("/api/scenarios/good", out -> out.write(RawStubServer.ascii("HTTP/1.1 201 Created\r\n"
                + "Content-Type: application/json\r\nX-Downstream: good\r\nContent-Length: 11\r\nConnection: close\r\n\r\n{\"ok\":true}")));
        Map<String, Integer> expectedStatus = Map.of("cut", 502, "invalid", 502, "good", 201);
        List<String> kinds = List.of("cut", "invalid", "good");

        // When
        ExecutorService clients = Executors.newFixedThreadPool(12);
        List<CompletableFuture<String>> outcomes = new ArrayList<>();
        try {
            for (int i = 0; i < 60; i++) {
                String kind = kinds.get(i % kinds.size());
                String requestId = "qa-conc-" + i;
                outcomes.add(CompletableFuture.supplyAsync(() -> {
                    HttpResponse<String> response = gateway.send(gateway.request("/api/scenarios/" + kind)
                            .header("Authorization", TestTokens.bearerValid())
                            .header("X-Request-Id", requestId)
                            .GET());
                    return check(kind, requestId, expectedStatus.get(kind), response);
                }, clients));
            }
            List<String> problems = outcomes.stream().map(CompletableFuture::join).filter(s -> !s.isEmpty()).toList();

            // Then
            assertThat(problems).as("responses that don't match their own request").isEmpty();
            assertThat(output.getAll()).doesNotContain(UNEXPECTED_GATEWAY_ERROR);
        } finally {
            clients.shutdownNow();
        }
    }

    @Test
    @Tag("AC-1.4")
    @DisplayName("T15 state isolation: after a 502 on a kept-alive client connection, the next response is a clean pass-through")
    void theNextResponseAfterAFailureIsClean() {
        // Given
        RAW_SCENARIO_SERVICE.on("/api/scenarios/cut", out -> out.write(RawStubServer.ascii("HTTP/1.1 200 OK\r\n"
                + "Content-Type: application/json\r\nContent-Length: 100\r\nX-Downstream: cut\r\nConnection: close\r\n\r\n{\"p\":")));
        RAW_SCENARIO_SERVICE.on("/api/scenarios/good", out -> out.write(RawStubServer.ascii("HTTP/1.1 200 OK\r\n"
                + "Content-Type: application/json\r\nX-Downstream: good\r\nContent-Length: 11\r\nConnection: close\r\n\r\n{\"ok\":true}")));

        // When the same client (HTTP/1.1 keep-alive) sends a failing request, then a good one
        HttpResponse<String> failed = gateway.send(gateway.request("/api/scenarios/cut")
                .header("Authorization", TestTokens.bearerValid()).header("X-Request-Id", "qa-seq-1").GET());
        HttpResponse<String> next = gateway.send(gateway.request("/api/scenarios/good")
                .header("Authorization", TestTokens.bearerValid()).header("X-Request-Id", "qa-seq-2").GET());

        // Then
        assertThat(failed.statusCode()).isEqualTo(502);
        assertThat(next.statusCode()).isEqualTo(200);
        assertThat(next.headers().allValues("X-Request-Id")).containsExactly("qa-seq-2");
        assertThat(next.headers().allValues("X-Downstream")).containsExactly("good");
        assertThat(next.headers().firstValue("Content-Type")).hasValue("application/json");
        assertThat(next.body()).isEqualTo("{\"ok\":true}");
    }

    private static String check(String kind, String requestId, int status, HttpResponse<String> response) {
        List<String> mismatches = new ArrayList<>();
        if (response.statusCode() != status) {
            mismatches.add("status " + response.statusCode());
        }
        if (!response.headers().allValues("X-Request-Id").equals(List.of(requestId))) {
            mismatches.add("X-Request-Id " + response.headers().allValues("X-Request-Id"));
        }
        List<String> downstream = response.headers().allValues("X-Downstream");
        if (status == 502 ? !downstream.isEmpty() : !downstream.equals(List.of(kind))) {
            mismatches.add("X-Downstream " + downstream);
        }
        if (status == 502 && !response.body().contains("\"requestId\":\"" + requestId + "\"")) {
            mismatches.add("body " + response.body());
        }
        if (status != 502 && !response.body().equals("{\"ok\":true}")) {
            mismatches.add("body " + response.body());
        }
        return mismatches.isEmpty() ? "" : kind + "/" + requestId + ": " + mismatches;
    }

    private static void assertProblemResponse(HttpResponse<String> response, int status, String title, String instance) {
        assertProblem(response.statusCode(), response.headers().firstValue("Content-Type").orElse(null),
                response.headers().firstValue("X-Request-Id").orElse(null), response.body(), status, title, instance);
    }
}
