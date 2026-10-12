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

/**
 * Detects downstream failures that happen after the response status line and headers arrive (D6, AC-4.6, AC-4.7).
 * It uses Spring Cloud Gateway Server MVC's {@link HttpHeadersFilter} extension point, which every proxied exchange
 * passes through:
 * <ul>
 *   <li>a {@link HttpHeadersFilter.RequestHttpHeadersFilter} records the exchange's request and when the exchange
 *       starts;</li>
 *   <li>a {@link HttpHeadersFilter.ResponseHttpHeadersFilter} runs when the downstream status and headers have
 *       arrived, before the gateway writes them to the client. It rejects a status code outside 100–599, records the
 *       gateway's own response headers, and replaces the downstream body stream
 *       ({@link MvcUtils#CLIENT_RESPONSE_INPUT_STREAM_ATTR}, the attribute Spring Cloud Gateway's body filters
 *       replace) with one that reports a read failure as a downstream failure.</li>
 * </ul>
 * Both failures are reported as {@link ResourceAccessException}, the type the HTTP client uses for every other
 * downstream I/O failure, so {@link GatewayExceptionHandler} answers 502, or 504 when the response wasn't complete
 * within the response timeout. Headers, status and body of a valid downstream response pass through unchanged.
 */
@Configuration(proxyBeanMethods = false)
public class DownstreamResponseFailureConfiguration {

    /** RFC 9110 section 15: status codes outside 100–599 are invalid. */
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

    /** Records the request Spring Cloud Gateway uses for the exchange, and when the exchange starts. */
    @Bean
    public HttpHeadersFilter.RequestHttpHeadersFilter downstreamExchangeRecorder() {
        return (headers, exchangeRequest) -> {
            HttpServletRequest servletRequest = exchangeRequest.servletRequest();
            servletRequest.setAttribute(EXCHANGE_REQUEST_ATTRIBUTE, exchangeRequest);
            servletRequest.setAttribute(EXCHANGE_START_ATTRIBUTE, System.nanoTime());
            return headers;
        };
    }

    /** Checks the downstream status and watches the body for failures; the headers pass through unchanged. */
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

    /** The response is rejected, so its connection is released here instead of after the body is copied. */
    private static void closeDownstreamResponse(ServerRequest exchangeRequest) {
        if (MvcUtils.getAttribute(exchangeRequest, MvcUtils.CLIENT_RESPONSE_ATTR)
                instanceof ClientHttpResponse downstreamResponse) {
            downstreamResponse.close();
        }
    }

    /** The headers the gateway has set so far: the request ID and, for allowed origins, CORS. */
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

    /**
     * A read failure once the response timeout has run out since the exchange started is a timeout: the HTTP client
     * closes the body when its response timeout expires. Any other read failure means the connection dropped.
     */
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
