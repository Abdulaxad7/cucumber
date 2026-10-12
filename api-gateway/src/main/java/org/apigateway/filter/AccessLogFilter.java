package org.apigateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.cloud.gateway.server.mvc.config.GatewayMvcProperties;
import org.springframework.cloud.gateway.server.mvc.config.RouteProperties;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Writes exactly one access log entry per request, including rejected ones (D7): request ID, method, path,
 * status, duration in milliseconds, the target service (only when a route handled the request) and the user ID
 * (only on authenticated protected requests). Bodies, query strings, {@code Authorization} values and tokens are
 * never logged, and the invitation token path segment is masked.
 * <p>
 * Runs inside the request ID filter, so entries carry the ID, and before Spring Security, so 401 and refused
 * CORS requests are logged too (P3).
 */
@Component
@Order(AccessLogFilter.ORDER)
public class AccessLogFilter extends OncePerRequestFilter {

    public static final int ORDER = RequestIdFilter.ORDER + 1;

    /** Logger name operators can route or tune separately from application logs. */
    public static final String LOGGER_NAME = "org.apigateway.access";

    /** Route metadata key naming the target service in {@code application.yaml}. */
    static final String SERVICE_METADATA_KEY = "service";

    static final String MASK = "***";

    /**
     * An invitation token grants org membership, so its path segment is never logged: it is the segment that
     * follows these resolved segments (D7).
     */
    private static final List<String> INVITATION_TOKEN_PARENT_SEGMENTS = List.of("api", "auth", "invitations");

    private static final String SEGMENT_SEPARATOR = "/";
    private static final String CURRENT_SEGMENT = ".";
    private static final String PARENT_SEGMENT = "..";
    private static final char PATH_PARAMETERS_START = ';';

    private static final Logger accessLog = LoggerFactory.getLogger(LOGGER_NAME);

    private final Map<String, String> servicesByRouteId;

    public AccessLogFilter(GatewayMvcProperties gatewayProperties) {
        this.servicesByRouteId = servicesByRouteId(gatewayProperties);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        boolean completed = false;
        try {
            chain.doFilter(request, response);
            completed = true;
        } finally {
            if (accessLog.isInfoEnabled()) {
                int status = completed ? response.getStatus() : HttpStatus.INTERNAL_SERVER_ERROR.value();
                long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                accessLog.info(entry(request, status, durationMs));
            }
        }
    }

    private String entry(HttpServletRequest request, int status, long durationMs) {
        StringBuilder entry = new StringBuilder()
                .append("requestId=").append(request.getAttribute(RequestIdFilter.ATTRIBUTE))
                .append(" method=").append(request.getMethod())
                .append(" path=").append(maskedPath(request.getRequestURI()))
                .append(" status=").append(status)
                .append(" durationMs=").append(durationMs);
        targetService(request).ifPresent(service -> entry.append(" service=").append(service));
        userId(request).ifPresent(userId -> entry.append(" userId=").append(userId));
        return entry.toString();
    }

    /**
     * {@code getRequestURI()} excludes the query string. Every segment that holds an invitation token is replaced by
     * {@value #MASK}, whatever form the path takes (AC-7.5, AC-7.7): segments are compared percent-decoded, without
     * path parameters and in any letter case, with empty and dot segments resolved, so encoded, duplicate-slash and
     * dot-segment paths are masked too. All other segments are logged as received.
     */
    static String maskedPath(String requestUri) {
        String[] segments = requestUri.split(SEGMENT_SEPARATOR, -1);
        List<String> resolvedSegments = new ArrayList<>();
        for (int i = 0; i < segments.length; i++) {
            String segment = comparableSegment(segments[i]);
            if (segment.isEmpty() || segment.equals(CURRENT_SEGMENT)) {
                continue;
            }
            if (segment.equals(PARENT_SEGMENT)) {
                if (!resolvedSegments.isEmpty()) {
                    resolvedSegments.remove(resolvedSegments.size() - 1);
                }
                continue;
            }
            if (followsInvitationTokenParent(resolvedSegments)) {
                segments[i] = MASK;
            }
            resolvedSegments.add(segment);
        }
        return String.join(SEGMENT_SEPARATOR, segments);
    }

    private static boolean followsInvitationTokenParent(List<String> resolvedSegments) {
        int parentStart = resolvedSegments.size() - INVITATION_TOKEN_PARENT_SEGMENTS.size();
        return parentStart >= 0 && resolvedSegments.subList(parentStart, resolvedSegments.size())
                .equals(INVITATION_TOKEN_PARENT_SEGMENTS);
    }

    /** The segment as a server compares it: without path parameters, percent-decoded and in lower case. */
    private static String comparableSegment(String rawSegment) {
        int parametersStart = rawSegment.indexOf(PATH_PARAMETERS_START);
        String segment = parametersStart < 0 ? rawSegment : rawSegment.substring(0, parametersStart);
        return percentDecoded(segment).toLowerCase(Locale.ROOT);
    }

    private static String percentDecoded(String segment) {
        try {
            return UriUtils.decode(segment, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException malformedEncoding) {
            // No server reads a malformed escape as a known segment, so the segment is compared as received.
            return segment;
        }
    }

    private Optional<String> targetService(HttpServletRequest request) {
        return request.getAttribute(MvcUtils.GATEWAY_ROUTE_ID_ATTR) instanceof String routeId
                ? Optional.ofNullable(servicesByRouteId.get(routeId))
                : Optional.empty();
    }

    private static Optional<String> userId(HttpServletRequest request) {
        return request.getAttribute(IdentityHeaderFilter.USER_ID_ATTRIBUTE) instanceof String userId
                ? Optional.of(userId)
                : Optional.empty();
    }

    private static Map<String, String> servicesByRouteId(GatewayMvcProperties gatewayProperties) {
        Map<String, String> services = new HashMap<>();
        gatewayProperties.getRoutes().forEach(route -> putService(services, route.getId(), route));
        gatewayProperties.getRoutesMap().forEach((key, route) ->
                putService(services, route.getId() != null ? route.getId() : key, route));
        return Map.copyOf(services);
    }

    private static void putService(Map<String, String> services, String routeId, RouteProperties route) {
        if (routeId != null && route.getMetadata().get(SERVICE_METADATA_KEY) instanceof String service) {
            services.put(routeId, service);
        }
    }
}
