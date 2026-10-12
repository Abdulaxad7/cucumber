package org.apigateway.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Authenticates protected requests that carry exactly one {@code Authorization} header with a valid
 * {@code Bearer <jwt>} (D4). A missing or invalid token, or more than one {@code Authorization} header (AC-2.9),
 * leaves the request unauthenticated, and the authorization rules then answer 401.
 * Public operations are skipped entirely: their token is not evaluated and is forwarded unchanged.
 * The header itself is never modified, so services receive it byte for byte.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** RFC 6750 section 2.1: the scheme is case-insensitive, the token is a b64token. */
    private static final Pattern BEARER_CREDENTIALS =
            Pattern.compile("^Bearer (?<token>[A-Za-z0-9\\-._~+/]+=*)$", Pattern.CASE_INSENSITIVE);

    private final JwtTokenValidator tokenValidator;
    private final RequestMatcher publicOperations;
    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return publicOperations.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        bearerToken(request)
                .flatMap(tokenValidator::validate)
                .ifPresent(this::authenticate);
        chain.doFilter(request, response);
    }

    /**
     * RFC 9110 allows one {@code Authorization} header. With several, a service could read a different one from the
     * one the gateway checked, so none of them authenticates the request.
     */
    private static Optional<String> bearerToken(HttpServletRequest request) {
        Enumeration<String> values = request.getHeaders(HttpHeaders.AUTHORIZATION);
        List<String> authorizations = values == null ? List.of() : Collections.list(values);
        if (authorizations.size() != 1) {
            return Optional.empty();
        }
        Matcher matcher = BEARER_CREDENTIALS.matcher(authorizations.get(0));
        return matcher.matches() ? Optional.of(matcher.group("token")) : Optional.empty();
    }

    private void authenticate(String userId) {
        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(new PreAuthenticatedAuthenticationToken(userId, null, AuthorityUtils.NO_AUTHORITIES));
        securityContextHolderStrategy.setContext(context);
    }
}
