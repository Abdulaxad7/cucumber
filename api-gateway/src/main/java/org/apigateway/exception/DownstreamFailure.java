package org.apigateway.exception;

import org.springframework.http.HttpStatus;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;

public enum DownstreamFailure {

    UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "The service handling this request is unavailable. Try again later."),

    TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "The service handling this request did not respond in time."),

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
