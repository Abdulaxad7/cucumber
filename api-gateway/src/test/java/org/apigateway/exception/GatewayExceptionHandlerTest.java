package org.apigateway.exception;

import jakarta.servlet.ServletException;
import org.apigateway.filter.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.client.ResourceAccessException;

import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayExceptionHandlerTest {

    private final GatewayExceptionHandler handler = new GatewayExceptionHandler();

    @Test
    void anUnexpectedFailureIsA500ProblemWithoutInternalDetails() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orgs");
        request.setAttribute(RequestIdFilter.ATTRIBUTE, "trace-500");

        // When
        ResponseEntity<ProblemDetail> response = handler.handleUnexpectedFailure(
                new IllegalStateException("No routeUri resolved for http://localhost:8081"), request);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        ProblemDetail problem = response.getBody();
        assertThat(problem).isNotNull();
        assertThat(problem.getType()).isEqualTo(URI.create("about:blank"));
        assertThat(problem.getTitle()).isEqualTo("Internal Server Error");
        assertThat(problem.getDetail()).isEqualTo("Unexpected gateway error.");
        assertThat(problem.getInstance()).isEqualTo(URI.create("/api/orgs"));
        assertThat(problem.getProperties()).containsEntry("requestId", "trace-500");
        assertThat(problem.toString()).doesNotContain("localhost", "IllegalStateException");
    }

    @Test
    void aDownstreamFailureWhileTheBodyIsCopiedIsA502Problem() {
        // Given Spring Cloud Gateway wrapped the failure while copying the body
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/scenarios/42");
        request.setAttribute(RequestIdFilter.ATTRIBUTE, "trace-502");
        ServletException wrapped = new ServletException(new ResourceAccessException("body read failed",
                new IOException("closed", new EOFException())));

        // When
        ResponseEntity<ProblemDetail> response = handler.handleWrappedFailure(wrapped, request,
                new MockHttpServletResponse());

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(502);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Bad Gateway");
        assertThat(response.getBody().getProperties()).containsEntry("requestId", "trace-502");
    }

    @Test
    void aResponseNotCompleteWithinTheResponseTimeoutIsA504Problem() {
        // Given
        HttpTimeoutException timeout = new HttpTimeoutException("not complete within the response timeout");
        timeout.initCause(new IOException("closed"));
        ServletException wrapped = new ServletException(new ResourceAccessException("body read failed", timeout));

        // When
        ResponseEntity<ProblemDetail> response = handler.handleWrappedFailure(wrapped,
                new MockHttpServletRequest("GET", "/api/scenarios/42"), new MockHttpServletResponse());

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(504);
    }

    @Test
    void anyOtherServletExceptionIsUnexpected() {
        // When
        ResponseEntity<ProblemDetail> response = handler.handleWrappedFailure(
                new ServletException("Handler dispatch failed"), new MockHttpServletRequest("GET", "/api/orgs"),
                new MockHttpServletResponse());

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(500);
    }

    @Test
    void theStatusAndHeadersOfTheFailedDownstreamResponseAreDiscarded() {
        // Given the downstream status and headers were written after the gateway's own headers were recorded
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/scenarios/42");
        HttpHeaders gatewayHeaders = new HttpHeaders();
        gatewayHeaders.add("X-Request-Id", "trace-discard");
        gatewayHeaders.add("Access-Control-Allow-Origin", "http://localhost:5173");
        request.setAttribute(GatewayExceptionHandler.GATEWAY_RESPONSE_HEADERS_ATTRIBUTE, gatewayHeaders);
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setHeader("X-Request-Id", "trace-discard");
        response.setHeader("Access-Control-Allow-Origin", "http://localhost:5173");
        response.setStatus(200);
        response.setHeader("Content-Length", "100");
        response.setHeader("Cache-Control", "max-age=60");

        // When
        handler.handleDownstreamFailure(new ResourceAccessException("body read failed", new IOException("closed")),
                request, response);

        // Then
        assertThat(response.getHeaderNames()).containsExactlyInAnyOrder("X-Request-Id", "Access-Control-Allow-Origin");
        assertThat(response.getHeaders("X-Request-Id")).containsExactly("trace-discard");
    }

    @Test
    void aFailureBeforeTheDownstreamResponseArrivedLeavesTheResponseAsItIs() {
        // Given no downstream response was written
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setHeader("X-Request-Id", "trace-connect");

        // When
        handler.handleDownstreamFailure(new ResourceAccessException("connect failed", new ConnectException()),
                new MockHttpServletRequest("GET", "/api/scenarios/42"), response);

        // Then
        assertThat(response.getHeaders("X-Request-Id")).containsExactly("trace-connect");
    }
}
