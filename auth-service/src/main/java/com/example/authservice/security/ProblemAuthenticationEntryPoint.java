package com.example.authservice.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

/**
 * Answers a protected request without valid credentials with a 401 Problem {@code UNAUTHENTICATED} and a
 * {@code WWW-Authenticate: Bearer} challenge (D2). The body is rendered by Spring MVC's exception resolution,
 * so it has the same format as every other error.
 */
@Component
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    static final String BEARER_CHALLENGE = "Bearer";

    private final HandlerExceptionResolver handlerExceptionResolver;

    public ProblemAuthenticationEntryPoint(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, BEARER_CHALLENGE);
        // Always "authentication required", whatever the original exception was (for example never a login failure).
        AuthenticationException required =
                new InsufficientAuthenticationException("Authentication is required", authException);
        if (handlerExceptionResolver.resolveException(request, response, null, required) == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }
}
