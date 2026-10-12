package com.example.authservice.service.impl;

import com.example.authservice.configuration.JwtProperties;
import com.example.authservice.dto.response.OrgClaim;
import com.example.authservice.model.User;
import com.example.authservice.repository.OrgMembershipRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MissingClaimException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Issues and validates the platform's JWTs (contract C2). Validation follows the gateway's rule: an HS512
 * signature with the shared secret, an {@code exp} in the future and a {@code sub} that is a UUID. The secret must be
 * standard Base64 of at least 64 bytes; otherwise the service doesn't start. Tokens are never logged.
 */
@Component
public class JwtTokenProvider {

    /** 512 bits, the minimum key size for HS512. */
    static final int MINIMUM_SECRET_BYTES = 64;

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);
    private static final String SECRET_SETTING = "app.jwtSecret";
    private static final String REQUIRED_ALGORITHM = Jwts.SIG.HS512.getId();

    private final SecretKey signingKey;

    private final long jwtExpirationInMs;

    private final OrgMembershipRepository orgMembershipRepository;

    private final JwtParser parser;

    public JwtTokenProvider(JwtProperties properties, OrgMembershipRepository orgMembershipRepository) {
        this.signingKey = Keys.hmacShaKeyFor(decodeSecret(properties.jwtSecret()));
        this.jwtExpirationInMs = properties.jwtExpirationInMs();
        this.orgMembershipRepository = orgMembershipRepository;
        this.parser = Jwts.parser()
                .verifyWith(signingKey)
                .build();
    }

    public String generateToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationInMs);

        List<OrgClaim> orgClaims = orgMembershipRepository.findByUser(user).stream()
                .map(membership -> new OrgClaim(membership.getOrganization().getId(), membership.getRole()))
                .collect(Collectors.toList());

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("orgs", orgClaims)
                .claim("mfa", user.isMfaEnabled())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey, Jwts.SIG.HS512)
                .compact();
    }

    /**
     * @return the user ID in the {@code sub} of a valid token
     * @throws JwtException             if the token is not valid (signature, algorithm, expiry, missing claim)
     * @throws IllegalArgumentException if the token is empty or its {@code sub} is not a UUID
     */
    public UUID getUserIdFromJWT(String token) {
        return UUID.fromString(parseValidClaims(token).getSubject());
    }

    /**
     * @throws JwtException             if the token is not valid
     * @throws IllegalArgumentException if the token is empty or its {@code sub} is not a UUID
     */
    public String getEmailFromJWT(String token) {
        return parseValidClaims(token).get("email", String.class);
    }

    /**
     * @return whether the token is valid; the reason a token is rejected is logged at DEBUG, without the token
     */
    public boolean validateToken(String authToken) {
        try {
            getUserIdFromJWT(authToken);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Token rejected: {}", ex.getClass().getSimpleName());
            return false;
        }
    }

    private Claims parseValidClaims(String token) {
        Jws<Claims> jws = parser.parseSignedClaims(token);
        // jjwt verifies with the algorithm named in the header, so HS256 or HS384 with the same key would pass.
        if (!REQUIRED_ALGORITHM.equals(jws.getHeader().getAlgorithm())) {
            throw new UnsupportedJwtException("Token algorithm is not " + REQUIRED_ALGORITHM);
        }
        Claims claims = jws.getPayload();
        if (claims.getExpiration() == null) {
            throw new MissingClaimException(jws.getHeader(), claims, Claims.EXPIRATION, null, "Token has no exp claim");
        }
        if (claims.getSubject() == null) {
            throw new MissingClaimException(jws.getHeader(), claims, Claims.SUBJECT, null, "Token has no sub claim");
        }
        UUID.fromString(claims.getSubject());
        return claims;
    }

    /** Error messages name the setting but never contain the value or any part of it. */
    private static byte[] decodeSecret(String base64Secret) {
        if (base64Secret.contains("${")) {
            throw new IllegalStateException(SECRET_SETTING + " must be set through the JWT_SECRET environment variable");
        }
        byte[] secret;
        try {
            secret = Base64.getDecoder().decode(base64Secret);
        } catch (IllegalArgumentException ex) {
            // The decoder's message quotes the offending character of the secret, so it is not chained.
            throw new IllegalStateException(SECRET_SETTING + " must be standard Base64 (RFC 4648 section 4)");
        }
        if (secret.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException(SECRET_SETTING + " must decode to at least " + MINIMUM_SECRET_BYTES
                    + " bytes for HS512, but it decodes to " + secret.length + " bytes");
        }
        return secret;
    }
}
