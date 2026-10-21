package org.scenarioservice.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D17 (P12, verified against Spring Boot 4.1's {@code SecurityFilterProperties}): the request-id filter runs before
 * the security chain, so lines logged inside it carry the request id; the user-id filter runs after it, once the
 * caller is known. {@code aop/OperationalLoggingTest} (AC-8.8) shows a line from inside the chain with a request id.
 */
class MdcFilterOrderTest {

    @Test
    @DisplayName("D17: request-id filter < security chain < user-id filter")
    void theMdcFiltersSurroundTheSecurityChain() {
        assertThat(RequestIdMdcFilter.ORDER).isLessThan(SecurityFilterProperties.DEFAULT_FILTER_ORDER);
        assertThat(UserIdMdcFilter.ORDER).isGreaterThan(SecurityFilterProperties.DEFAULT_FILTER_ORDER);
    }
}
