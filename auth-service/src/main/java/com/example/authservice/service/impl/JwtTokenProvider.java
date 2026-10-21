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
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class JwtTokenProvider {

    static final int MINIMUM_SECRET_BYTES = 64;

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

    public UUID getUserIdFromJWT(String token) {
        return UUID.fromString(parseValidClaims(token).getSubject());
    }

    private Claims parseValidClaims(String token) {
        Jws<Claims> jws = parser.parseSignedClaims(token);
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

    private static byte[] decodeSecret(String base64Secret) {
        if (base64Secret.contains("${")) {
            throw new IllegalStateException(SECRET_SETTING + " must be set through the JWT_SECRET environment variable");
        }
        byte[] secret;
        try {
            secret = Base64.getDecoder().decode(base64Secret);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(SECRET_SETTING + " must be standard Base64 (RFC 4648 section 4)");
        }
        if (secret.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException(SECRET_SETTING + " must decode to at least " + MINIMUM_SECRET_BYTES
                    + " bytes for HS512, but it decodes to " + secret.length + " bytes");
        }
        return secret;
    }
}
