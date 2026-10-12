package org.apigateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import org.apigateway.configuration.JwtProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Validates bearer tokens locally, without calling auth-service (D4, contract C2): an HS512 signature with
 * the shared secret, an {@code exp} in the future (no clock skew) and a {@code sub} of 1 or more printable ASCII
 * characters. Unsigned ({@code alg: none}) and tampered tokens are rejected. Tokens and subjects are never logged.
 */
@Component
public class JwtTokenValidator {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenValidator.class);
    private static final String REQUIRED_ALGORITHM = Jwts.SIG.HS512.getId();

    /**
     * D4 (AC-2.10): 1 or more printable ASCII characters, U+0021–U+007E. {@code sub} is copied into
     * {@code X-User-Id} and the space-separated access log, so spaces, control and non-ASCII characters are refused.
     */
    private static final Pattern VALID_SUBJECT = Pattern.compile("[\\x21-\\x7E]+");

    private final JwtParser parser;

    public JwtTokenValidator(JwtProperties properties) {
        this.parser = Jwts.parser()
                .verifyWith(JwtSecretDecoder.decode(properties.secret()))
                .build();
    }

    /**
     * @return the token's subject (the caller's user ID) if the token is valid, otherwise empty
     */
    public Optional<String> validate(String token) {
        try {
            Jws<Claims> jws = parser.parseSignedClaims(token);
            if (!REQUIRED_ALGORITHM.equals(jws.getHeader().getAlgorithm())) {
                log.debug("Bearer token rejected: algorithm is not {}", REQUIRED_ALGORITHM);
                return Optional.empty();
            }
            Claims claims = jws.getPayload();
            if (claims.getExpiration() == null) {
                log.debug("Bearer token rejected: no exp claim");
                return Optional.empty();
            }
            String subject = claims.getSubject();
            if (subject == null || !VALID_SUBJECT.matcher(subject).matches()) {
                log.debug("Bearer token rejected: sub is missing or not printable ASCII");
                return Optional.empty();
            }
            return Optional.of(subject);
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Bearer token rejected: {}", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
