package com.example.authservice.configuration;

import com.example.authservice.enums.UserStatus;
import com.example.authservice.security.UserPrincipal;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The user-ID MDC filter (D17 item 2): the authenticated {@link UserPrincipal}'s id, nothing for anonymous callers,
 * and the key is removed after the request, also when it fails.
 */
class UserIdMdcFilterTest {

    private static final UUID ANA = UUID.fromString("0f8c9e2a-1b3d-4e5f-8a9b-0c1d2e3f4a5b");

    private final UserIdMdcFilter filter = new UserIdMdcFilter();
    private final AtomicReference<String> seenByTheChain = new AtomicReference<>();
    private final FilterChain recordingChain = (request, response) -> seenByTheChain.set(MDC.get("userId"));

    @AfterEach
    void clearContexts() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    @Tag("AC-9.6")
    void anAuthenticatedRequestCarriesTheUserIdAndNeverTheEmail() throws Exception {
        authenticateAna();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), recordingChain);

        assertThat(seenByTheChain.get()).isEqualTo(ANA.toString());
        assertThat(MDC.get("userId")).isNull();
    }

    @Test
    @Tag("AC-9.6")
    void anUnauthenticatedRequestHasNoUserId() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), recordingChain);

        assertThat(seenByTheChain.get()).isNull();
    }

    @Test
    @Tag("AC-9.6")
    void anAnonymousRequestHasNoUserId() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), recordingChain);

        assertThat(seenByTheChain.get()).isNull();
    }

    @Test
    @Tag("AC-9.8")
    void theUserIdIsRemovedWhenTheRequestFails() {
        authenticateAna();
        FilterChain failingChain = (request, response) -> {
            throw new IllegalStateException("boom");
        };

        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                failingChain)).isInstanceOf(IllegalStateException.class);

        assertThat(MDC.get("userId")).isNull();
    }

    @Test
    void itRunsAfterTheSpringSecurityFilterChain() {
        assertThat(UserIdMdcFilter.ORDER).isGreaterThan(-100);
    }

    private static void authenticateAna() {
        UserPrincipal ana = new UserPrincipal(ANA, "ana@example.com", "hash", List.of(), UserStatus.PENDING_VERIFICATION);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(ana, "token", ana.getAuthorities()));
    }
}
