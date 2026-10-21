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

@Component
@Order(RequestIdFilter.ORDER)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final int ORDER = SecurityFilterProperties.DEFAULT_FILTER_ORDER - 2;

    public static final String HEADER_NAME = "X-Request-Id";

    public static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";

    public static final String MDC_KEY = "requestId";

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
