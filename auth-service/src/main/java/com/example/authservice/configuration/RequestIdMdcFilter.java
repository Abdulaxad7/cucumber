package com.example.authservice.configuration;

import com.example.authservice.exception.Problems;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.slf4j.MDC;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(RequestIdMdcFilter.ORDER)
public class RequestIdMdcFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "requestId";

    static final int ORDER = SecurityFilterProperties.DEFAULT_FILTER_ORDER - 10;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        MDC.put(MDC_KEY, Problems.requestId(request).orElseGet(() -> UUID.randomUUID().toString()));
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
