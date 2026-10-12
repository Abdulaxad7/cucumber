package org.apigateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Assigns every request its request ID (D7). A well-formed client {@code X-Request-Id} is kept;
 * anything else is replaced by a generated UUID. The ID is forwarded downstream, returned on the response,
 * stored as a request attribute for error bodies and the access log, and put in the logging MDC.
 * <p>
 * Runs before Spring Security, so requests rejected with 401 get an ID too (P3).
 */
@Component
@Order(RequestIdFilter.ORDER)
public class RequestIdFilter extends OncePerRequestFilter {

    /** Before the Spring Security filter chain. */
    public static final int ORDER = SecurityFilterProperties.DEFAULT_FILTER_ORDER - 2;

    public static final String HEADER_NAME = "X-Request-Id";

    /** Request attribute holding the effective request ID. */
    public static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";

    public static final String MDC_KEY = "requestId";

    /** Client values that are kept; anything else could be used for log injection. */
    static final Pattern VALID_CLIENT_REQUEST_ID = Pattern.compile("^[A-Za-z0-9._-]{1,128}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(HEADER_NAME));
        request.setAttribute(ATTRIBUTE, requestId);
        response.setHeader(HEADER_NAME, requestId);
        MDC.put(MDC_KEY, requestId);
        try {
            chain.doFilter(new HeaderOverridingRequest(request, HEADER_NAME, requestId), response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    static String resolveRequestId(@Nullable String clientValue) {
        if (clientValue != null && VALID_CLIENT_REQUEST_ID.matcher(clientValue).matches()) {
            return clientValue;
        }
        return UUID.randomUUID().toString();
    }
}
