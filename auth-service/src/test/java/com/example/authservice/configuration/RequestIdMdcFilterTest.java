package com.example.authservice.configuration;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The request-ID MDC filter (D17 item 2): a valid {@code X-Request-Id} is used as is, anything else is replaced by
 * a new UUID, and the key is removed after the request, also when it fails.
 */
class RequestIdMdcFilterTest {

    private final RequestIdMdcFilter filter = new RequestIdMdcFilter();
    private final AtomicReference<String> seenByTheChain = new AtomicReference<>();
    private final FilterChain recordingChain = (request, response) -> seenByTheChain.set(MDC.get("requestId"));

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @Tag("AC-9.6")
    void aValidRequestIdHeaderIsTheRequestId() throws Exception {
        filter.doFilter(request("req-96"), new MockHttpServletResponse(), recordingChain);

        assertThat(seenByTheChain.get()).isEqualTo("req-96");
        assertThat(MDC.get("requestId")).isNull();
    }

    @ParameterizedTest(name = "AC-9.7 the header \"{0}\" is replaced by a new UUID")
    @ValueSource(strings = {"bad id QA", "", "req\n2026-10-13 ERROR forged", "aéb"})
    @Tag("AC-9.7")
    void anInvalidRequestIdHeaderIsReplacedByANewUuid(String header) throws Exception {
        filter.doFilter(request(header), new MockHttpServletResponse(), recordingChain);

        assertThat(UUID.fromString(seenByTheChain.get()).toString()).isEqualTo(seenByTheChain.get());
        assertThat(seenByTheChain.get()).isNotEqualTo(header);
    }

    @Test
    @Tag("AC-9.7")
    void aRequestIdOf129CharactersIsReplacedAndOf128IsKept() throws Exception {
        filter.doFilter(request("a".repeat(129)), new MockHttpServletResponse(), recordingChain);
        assertThat(seenByTheChain.get()).isNotEqualTo("a".repeat(129)).hasSize(36);

        filter.doFilter(request("a".repeat(128)), new MockHttpServletResponse(), recordingChain);
        assertThat(seenByTheChain.get()).isEqualTo("a".repeat(128));
    }

    @Test
    @Tag("AC-9.7")
    void aMissingRequestIdIsANewUuidPerRequest() throws Exception {
        filter.doFilter(new MockHttpServletRequest("GET", "/api/orgs"), new MockHttpServletResponse(), recordingChain);
        String first = seenByTheChain.get();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/orgs"), new MockHttpServletResponse(), recordingChain);

        assertThat(UUID.fromString(first).toString()).isEqualTo(first);
        assertThat(seenByTheChain.get()).isNotEqualTo(first);
    }

    @Test
    @Tag("AC-9.8")
    void theRequestIdIsRemovedWhenTheRequestFails() {
        FilterChain failingChain = (request, response) -> {
            throw new IllegalStateException("boom");
        };

        assertThatThrownBy(() -> filter.doFilter(request("req-98"), new MockHttpServletResponse(), failingChain))
                .isInstanceOf(IllegalStateException.class);

        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void itRunsBeforeTheSpringSecurityFilterChain() {
        assertThat(RequestIdMdcFilter.ORDER).isLessThan(-100);
    }

    private static MockHttpServletRequest request(String requestId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orgs");
        request.addHeader("X-Request-Id", requestId);
        return request;
    }
}
