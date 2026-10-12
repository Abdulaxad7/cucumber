package org.apigateway.exception;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.ProtocolException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class DownstreamFailureTest {

    @Test
    void connectionFailuresMeanTheServiceIsUnavailable() {
        assertThat(DownstreamFailure.classify(wrapped(new ConnectException()))).isEqualTo(DownstreamFailure.UNAVAILABLE);
        assertThat(DownstreamFailure.classify(wrapped(new UnknownHostException("scenario-service"))))
                .isEqualTo(DownstreamFailure.UNAVAILABLE);
        assertThat(DownstreamFailure.classify(wrapped(new HttpConnectTimeoutException("connect timed out"))))
                .isEqualTo(DownstreamFailure.UNAVAILABLE);
    }

    @Test
    void aResponseTimeoutIsAGatewayTimeout() {
        assertThat(DownstreamFailure.classify(wrapped(new HttpTimeoutException("request timed out"))))
                .isEqualTo(DownstreamFailure.TIMEOUT);
    }

    @Test
    void anyOtherIoFailureIsABadResponse() {
        assertThat(DownstreamFailure.classify(wrapped(new IOException("HTTP/1.1 header parser received no bytes"))))
                .isEqualTo(DownstreamFailure.BAD_RESPONSE);
        assertThat(DownstreamFailure.classify(wrapped(new EOFException()))).isEqualTo(DownstreamFailure.BAD_RESPONSE);
    }

    @Test
    void anIncompleteOrInvalidResponseIsABadResponse() {
        assertThat(DownstreamFailure.classify(wrapped(new IOException("closed",
                new IOException("fixed content-length: 100, bytes received: 11", new EOFException())))))
                .isEqualTo(DownstreamFailure.BAD_RESPONSE);
        assertThat(DownstreamFailure.classify(wrapped(new ProtocolException("invalid status code"))))
                .isEqualTo(DownstreamFailure.BAD_RESPONSE);
    }

    @Test
    void aResponseNotCompleteWithinTheResponseTimeoutIsAGatewayTimeout() {
        // Given the body read failed because the response timeout closed it
        HttpTimeoutException timeout = new HttpTimeoutException("not complete within the response timeout");
        timeout.initCause(new IOException("closed"));

        // Then
        assertThat(DownstreamFailure.classify(wrapped(timeout))).isEqualTo(DownstreamFailure.TIMEOUT);
    }

    @Test
    void statusesMatchTheErrorTable() {
        assertThat(DownstreamFailure.UNAVAILABLE.status().value()).isEqualTo(503);
        assertThat(DownstreamFailure.TIMEOUT.status().value()).isEqualTo(504);
        assertThat(DownstreamFailure.BAD_RESPONSE.status().value()).isEqualTo(502);
    }

    private static ResourceAccessException wrapped(IOException cause) {
        return new ResourceAccessException("I/O error on GET request for \"http://localhost:8082/api/scenarios/42\"", cause);
    }
}
