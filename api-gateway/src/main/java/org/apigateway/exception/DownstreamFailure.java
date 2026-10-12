package org.apigateway.exception;

import org.springframework.http.HttpStatus;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;

/**
 * How a failed downstream call is reported to the client (D6). The gateway never retries. The only replay is the
 * HTTP client's single resend of a GET or HEAD whose connection closed before any response byte arrived (AC-4.8).
 */
public enum DownstreamFailure {

    /** Could not connect: connection refused, unknown host or connect timeout. */
    UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "The service handling this request is unavailable. Try again later."),

    /**
     * Connected, but the response (status line, headers and the whole body) wasn't complete within the response
     * timeout.
     */
    TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "The service handling this request did not respond in time."),

    /**
     * The connection dropped before the response was complete, or the response was invalid: a malformed status
     * line, a status code outside 100–599, or a body shorter than its declared length.
     */
    BAD_RESPONSE(HttpStatus.BAD_GATEWAY, "The service handling this request returned an invalid response.");

    private final HttpStatus status;
    private final String detail;

    DownstreamFailure(HttpStatus status, String detail) {
        this.status = status;
        this.detail = detail;
    }

    public HttpStatus status() {
        return status;
    }

    public String detail() {
        return detail;
    }

    /**
     * Classifies the I/O failure of a downstream call by the first recognised cause in its chain.
     * Connect timeouts are checked before response timeouts because the former extend the latter.
     */
    public static DownstreamFailure classify(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpConnectTimeoutException || cause instanceof ConnectException
                    || cause instanceof UnknownHostException || cause instanceof NoRouteToHostException) {
                return UNAVAILABLE;
            }
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
                return TIMEOUT;
            }
        }
        return BAD_RESPONSE;
    }
}
