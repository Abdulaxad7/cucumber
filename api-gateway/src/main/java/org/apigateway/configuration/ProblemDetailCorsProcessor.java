package org.apigateway.configuration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apigateway.exception.GatewayProblems;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsProcessor;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

@Component
public class ProblemDetailCorsProcessor implements CorsProcessor {

    static final String ORIGIN_NOT_ALLOWED_DETAIL = "This origin is not allowed to call the API.";

    private final CorsProcessor rules = new SilentlyRejectingCorsProcessor();
    private final HandlerExceptionResolver handlerExceptionResolver;

    public ProblemDetailCorsProcessor(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Override
    public boolean processRequest(@Nullable CorsConfiguration configuration, HttpServletRequest request,
                                  HttpServletResponse response) throws IOException {
        if (rules.processRequest(configuration, request, response)) {
            return true;
        }
        ErrorResponseException refusal = new ErrorResponseException(HttpStatus.FORBIDDEN,
                GatewayProblems.of(HttpStatus.FORBIDDEN, ORIGIN_NOT_ALLOWED_DETAIL, request), null);
        handlerExceptionResolver.resolveException(request, response, null, refusal);
        return false;
    }

    private static final class SilentlyRejectingCorsProcessor extends DefaultCorsProcessor {

        @Override
        protected void rejectRequest(ServerHttpResponse response) {
        }
    }
}
