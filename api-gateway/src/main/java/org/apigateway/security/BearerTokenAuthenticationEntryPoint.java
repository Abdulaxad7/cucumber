package org.apigateway.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Answers protected requests without a valid token with 401 and a bearer challenge (D4, AC-2.7).
 * The body is the shared Problem Details format: the failure is handed to Spring MVC's exception
 * resolution, which renders it through {@link org.apigateway.exception.GatewayExceptionHandler}.
 */
@Component
public class BearerTokenAuthenticationEntryPoint implements AuthenticationEntryPoint {

    static final String BEARER_CHALLENGE = "Bearer";

    private final HandlerExceptionResolver handlerExceptionResolver;

    public BearerTokenAuthenticationEntryPoint(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, BEARER_CHALLENGE);
        handlerExceptionResolver.resolveException(request, response, null, authException);
    }
}
