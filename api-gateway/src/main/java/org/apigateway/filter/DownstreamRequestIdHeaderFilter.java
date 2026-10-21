package org.apigateway.filter;

import org.springframework.cloud.gateway.server.mvc.filter.HttpHeadersFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerResponse;

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
