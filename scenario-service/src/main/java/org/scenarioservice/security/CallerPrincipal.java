package org.scenarioservice.security;

import java.util.UUID;

/**
 * The authenticated caller (D6): the user id from the token's {@code sub}, and the raw token, which only the access
 * checker reads, to forward it to auth-service (D4). scenario-service has no user table; the {@code email} and
 * {@code orgs} claims and the {@code X-User-Id} header are never used. {@link #toString()} never shows the token.
 */
public record CallerPrincipal(UUID userId, String token) {

    @Override
    public String toString() {
        return "CallerPrincipal[userId=" + userId + ", token=<redacted>]";
    }
}
