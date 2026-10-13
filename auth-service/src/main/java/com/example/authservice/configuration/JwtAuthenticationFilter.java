package com.example.authservice.configuration;

import com.example.authservice.service.impl.CustomUserDetailsService;
import com.example.authservice.service.impl.JwtTokenProvider;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService customUserDetailsService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String jwt = getJwtFromRequest(request);

        if (StringUtils.hasText(jwt)) {
            authenticate(jwt, request);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String jwt, HttpServletRequest request) {
        UUID userId;
        try {
            userId = tokenProvider.getUserIdFromJWT(jwt);
        } catch (JwtException | IllegalArgumentException ex) {
            logger.debug("Bearer token rejected: " + ex.getClass().getSimpleName());
            return;
        }

        UserDetails userDetails;
        try {
            userDetails = customUserDetailsService.loadUserById(userId);
        } catch (UsernameNotFoundException ex) {
            logger.debug("Bearer token rejected: its user does not exist");
            return;
        }
        if (!userDetails.isEnabled()) {
            logger.debug("Bearer token rejected: its user is suspended");
            return;
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userDetails, jwt, userDetails.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
