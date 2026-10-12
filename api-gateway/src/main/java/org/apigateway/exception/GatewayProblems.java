package org.apigateway.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.apigateway.filter.RequestIdFilter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.net.URI;

/**
 * Builds the RFC 9457 body of every gateway-generated error (D6): {@code type} {@code about:blank},
 * {@code title} (reason phrase), {@code status}, {@code detail}, {@code instance} (path without query string)
 * and {@code requestId}. Bodies never carry exception details or downstream locations.
 */
public final class GatewayProblems {

    public static final String UNAUTHORIZED_DETAIL = "A valid bearer token is required.";
    public static final String NOT_FOUND_DETAIL = "No API route matches this path.";
    public static final String INTERNAL_ERROR_DETAIL = "Unexpected gateway error.";

    static final URI ABOUT_BLANK = URI.create("about:blank");
    static final String REQUEST_ID_PROPERTY = "requestId";

    private GatewayProblems() {
    }

    public static ProblemDetail of(HttpStatus status, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        return completed(problem, request);
    }

    /**
     * Fills in the contract fields of a problem, including one created elsewhere (for example by Spring MVC):
     * {@code type} when unset (Spring leaves it empty), {@code instance} and {@code requestId}.
     */
    public static ProblemDetail completed(ProblemDetail problem, HttpServletRequest request) {
        if (problem.getType() == null) {
            problem.setType(ABOUT_BLANK);
        }
        problem.setInstance(URI.create(request.getRequestURI()));
        if (request.getAttribute(RequestIdFilter.ATTRIBUTE) instanceof String requestId) {
            problem.setProperty(REQUEST_ID_PROPERTY, requestId);
        }
        return problem;
    }
}
