package org.apigateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

public class IdentityHeaderFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-User-Id";

    public static final String USER_ID_ATTRIBUTE = IdentityHeaderFilter.class.getName() + ".userId";

    private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();
    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<String> userId = authenticatedUserId();
        userId.ifPresent(id -> request.setAttribute(USER_ID_ATTRIBUTE, id));
        chain.doFilter(new HeaderOverridingRequest(request, HEADER_NAME, userId.orElse(null)), response);
    }

    private Optional<String> authenticatedUserId() {
        Authentication authentication = securityContextHolderStrategy.getContext().getAuthentication();
        return trustResolver.isAuthenticated(authentication) ? Optional.of(authentication.getName()) : Optional.empty();
    }
}
