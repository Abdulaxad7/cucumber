package org.apigateway.exception;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks a gateway-generated error against the Problem Details contract (D6, AC-5.1).
 */
final class ProblemAssertions {

    private ProblemAssertions() {
    }

    static void assertProblem(HttpResponse<String> response, int status, String title, String instance) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json");
        DocumentContext body = JsonPath.parse(response.body());
        assertThat(body.read("$.type", String.class)).isEqualTo("about:blank");
        assertThat(body.read("$.status", Integer.class)).isEqualTo(status);
        assertThat(body.read("$.title", String.class)).isEqualTo(title);
        assertThat(body.read("$.instance", String.class)).isEqualTo(instance);
        assertThat(body.read("$.detail", String.class)).isNotBlank();
        assertThat(body.read("$.requestId", String.class))
                .isEqualTo(response.headers().firstValue("X-Request-Id").orElseThrow());
    }
}
