package org.apigateway.configuration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apigateway.exception.GatewayExceptionHandler;
import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.cloud.gateway.server.mvc.filter.HttpHeadersFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.function.ServerRequest;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ProtocolException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;

@Configuration(proxyBeanMethods = false)
public class DownstreamResponseFailureConfiguration {

    private static final int LOWEST_VALID_STATUS = 100;
    private static final int HIGHEST_VALID_STATUS = 599;

    private static final String EXCHANGE_REQUEST_ATTRIBUTE =
            DownstreamResponseFailureConfiguration.class.getName() + ".exchangeRequest";
    private static final String EXCHANGE_START_ATTRIBUTE =
            DownstreamResponseFailureConfiguration.class.getName() + ".exchangeStartNanos";

    private static final String INVALID_STATUS = "The downstream response has an invalid status code";
    private static final String BODY_READ_FAILED = "The downstream response body could not be read";
    private static final String BODY_NOT_COMPLETE_IN_TIME =
            "The downstream response was not complete within the response timeout";

    @Bean
    public HttpHeadersFilter.RequestHttpHeadersFilter downstreamExchangeRecorder() {
        return (headers, exchangeRequest) -> {
            HttpServletRequest servletRequest = exchangeRequest.servletRequest();
            servletRequest.setAttribute(EXCHANGE_REQUEST_ATTRIBUTE, exchangeRequest);
            servletRequest.setAttribute(EXCHANGE_START_ATTRIBUTE, System.nanoTime());
            return headers;
        };
    }

    @Bean
    public HttpHeadersFilter.ResponseHttpHeadersFilter downstreamResponseFailureDetector(
            DownstreamProperties downstreamProperties) {
        Duration responseTimeout = downstreamProperties.responseTimeout();
        return (headers, downstreamResponse) -> {
            ServletRequestAttributes current =
                    (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            HttpServletRequest servletRequest = current.getRequest();
            ServerRequest exchangeRequest = (ServerRequest) servletRequest.getAttribute(EXCHANGE_REQUEST_ATTRIBUTE);

            int status = downstreamResponse.statusCode().value();
            if (status < LOWEST_VALID_STATUS || status > HIGHEST_VALID_STATUS) {
                closeDownstreamResponse(exchangeRequest);
                throw new ResourceAccessException(INVALID_STATUS, new ProtocolException(INVALID_STATUS));
            }

            servletRequest.setAttribute(GatewayExceptionHandler.GATEWAY_RESPONSE_HEADERS_ATTRIBUTE,
                    headersOf(current.getResponse()));
            if (MvcUtils.getAttribute(exchangeRequest, MvcUtils.CLIENT_RESPONSE_INPUT_STREAM_ATTR)
                    instanceof InputStream body) {
                long exchangeStart = (Long) servletRequest.getAttribute(EXCHANGE_START_ATTRIBUTE);
                MvcUtils.putAttribute(exchangeRequest, MvcUtils.CLIENT_RESPONSE_INPUT_STREAM_ATTR,
                        reportingReadFailures(body, exchangeStart, responseTimeout));
            }
            return headers;
        };
    }

    private static void closeDownstreamResponse(ServerRequest exchangeRequest) {
        if (MvcUtils.getAttribute(exchangeRequest, MvcUtils.CLIENT_RESPONSE_ATTR)
                instanceof ClientHttpResponse downstreamResponse) {
            downstreamResponse.close();
        }
    }

    private static HttpHeaders headersOf(HttpServletResponse response) {
        HttpHeaders headers = new HttpHeaders();
        response.getHeaderNames().forEach(name -> headers.addAll(name, List.copyOf(response.getHeaders(name))));
        return headers;
    }

    private static InputStream reportingReadFailures(InputStream body, long exchangeStartNanos,
                                                     Duration responseTimeout) {
        return new FilterInputStream(body) {

            @Override
            public int read() throws IOException {
                try {
                    return super.read();
                } catch (IOException failure) {
                    throw downstreamFailure(failure, exchangeStartNanos, responseTimeout);
                }
            }

            @Override
            public int read(byte[] buffer, int offset, int length) throws IOException {
                try {
                    return super.read(buffer, offset, length);
                } catch (IOException failure) {
                    throw downstreamFailure(failure, exchangeStartNanos, responseTimeout);
                }
            }
        };
    }

    private static ResourceAccessException downstreamFailure(IOException failure, long exchangeStartNanos,
                                                             Duration responseTimeout) {
        if (System.nanoTime() - exchangeStartNanos < responseTimeout.toNanos()) {
            return new ResourceAccessException(BODY_READ_FAILED, failure);
        }
        HttpTimeoutException timeout = new HttpTimeoutException(BODY_NOT_COMPLETE_IN_TIME);
        timeout.initCause(failure);
        return new ResourceAccessException(BODY_READ_FAILED, timeout);
    }
}
