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

/**
 * Tells services which user made a request (D4, contract C3). Every client-supplied {@code X-User-Id}, in any
 * letter case, is removed on every request. On authenticated (protected) requests the header is set to the
 * validated token's {@code sub}, read from the security context rather than from the token. The value is
 * informational: services must not authorize on it.
 * <p>
 * Runs inside the security filter chain after authorization. The user ID is also stored as a request
 * attribute for the access log.
 */
public class IdentityHeaderFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-User-Id";

    /** Request attribute holding the authenticated user ID; absent on public and rejected requests. */
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
