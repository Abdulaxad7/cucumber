package org.apigateway.exception;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps gateway failures to Problem Details (D6). Responses produced by downstream services never pass
 * through here: the gateway forwards them unchanged.
 */
@RestControllerAdvice
public class GatewayExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Request attribute holding the gateway's own response headers ({@link HttpHeaders}), recorded when a downstream
     * response arrives and before its status and headers are written to the client. If the downstream call fails
     * after that, the downstream status and headers are discarded and these headers are restored, so the Problem
     * response carries nothing from the failed downstream response.
     */
    public static final String GATEWAY_RESPONSE_HEADERS_ATTRIBUTE =
            GatewayExceptionHandler.class.getName() + ".gatewayResponseHeaders";

    private static final Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    /** Missing, invalid or expired token on a protected path; raised through the authentication entry point. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthenticationFailure(AuthenticationException ex,
                                                                     HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, GatewayProblems.UNAUTHORIZED_DETAIL, request);
    }

    /**
     * I/O failure calling a downstream service, or an invalid downstream response, whether it happens before or
     * after the response headers arrive (AC-4.6, AC-4.7). Logged at WARN with the route, the status and the failure
     * type only: the message is never logged because it can contain the downstream URL.
     */
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ProblemDetail> handleDownstreamFailure(ResourceAccessException ex,
                                                                 HttpServletRequest request,
                                                                 HttpServletResponse response) {
        DownstreamFailure failure = DownstreamFailure.classify(ex);
        log.warn("Downstream call failed: route={} status={} cause={}",
                request.getAttribute(MvcUtils.GATEWAY_ROUTE_ID_ATTR), failure.status().value(), ioFailureName(ex));
        discardDownstreamResponse(request, response);
        return problem(failure.status(), failure.detail(), request);
    }

    /**
     * Spring Cloud Gateway reports a failure while it copies the downstream response body wrapped in a
     * {@link ServletException}. A wrapped downstream failure is handled as such; anything else is unexpected.
     */
    @ExceptionHandler(ServletException.class)
    public ResponseEntity<ProblemDetail> handleWrappedFailure(ServletException ex, HttpServletRequest request,
                                                              HttpServletResponse response) {
        if (ex.getCause() instanceof ResourceAccessException downstreamFailure) {
            return handleDownstreamFailure(downstreamFailure, request, response);
        }
        return handleUnexpectedFailure(ex, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpectedFailure(Exception ex, HttpServletRequest request) {
        log.error("Unexpected gateway error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, GatewayProblems.INTERNAL_ERROR_DETAIL, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleNoResourceFoundException(NoResourceFoundException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return noRoute(request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleNoHandlerFoundException(NoHandlerFoundException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return noRoute(request);
    }

    /** Gives the other Spring MVC errors (for example 405) the same type, instance and requestId fields. */
    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body,
            HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem && request instanceof ServletWebRequest servletRequest) {
            GatewayProblems.completed(problem, servletRequest.getRequest());
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    private ResponseEntity<Object> noRoute(WebRequest request) {
        HttpServletRequest servletRequest = ((ServletWebRequest) request).getRequest();
        ProblemDetail problem = GatewayProblems.of(HttpStatus.NOT_FOUND, GatewayProblems.NOT_FOUND_DETAIL, servletRequest);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail, HttpServletRequest request) {
        return ResponseEntity.status(status).body(GatewayProblems.of(status, detail, request));
    }

    /**
     * Clears the downstream status and headers written before the failure and restores the gateway's own headers
     * (request ID, CORS). Once the response is committed it can no longer change (FU-4).
     */
    private static void discardDownstreamResponse(HttpServletRequest request, HttpServletResponse response) {
        if (request.getAttribute(GATEWAY_RESPONSE_HEADERS_ATTRIBUTE) instanceof HttpHeaders gatewayHeaders
                && !response.isCommitted()) {
            response.reset();
            gatewayHeaders.forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));
        }
    }

    /** The I/O exception the HTTP client reported, for example ConnectException or HttpTimeoutException. */
    private static String ioFailureName(ResourceAccessException failure) {
        Throwable cause = failure.getCause() != null ? failure.getCause() : failure;
        return cause.getClass().getSimpleName();
    }
}
