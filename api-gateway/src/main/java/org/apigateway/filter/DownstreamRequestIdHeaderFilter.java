package org.apigateway.filter;

import org.springframework.cloud.gateway.server.mvc.filter.HttpHeadersFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Drops every {@code X-Request-Id} header, in any letter case, from downstream responses before the gateway copies
 * them (D7, AC-7.6, contract C3). The response then carries exactly one request ID: the one {@link RequestIdFilter}
 * set. Every other downstream header passes through unchanged.
 */
@Component
public class DownstreamRequestIdHeaderFilter implements HttpHeadersFilter.ResponseHttpHeadersFilter {

    @Override
    public HttpHeaders apply(HttpHeaders downstreamHeaders, ServerResponse response) {
        HttpHeaders forwarded = new HttpHeaders();
        downstreamHeaders.forEach((name, values) -> {
            if (!RequestIdFilter.HEADER_NAME.equalsIgnoreCase(name)) {
                forwarded.addAll(name, values);
            }
        });
        return forwarded;
    }
}
