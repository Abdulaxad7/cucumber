package com.example.authservice.service.impl;

import com.example.authservice.dto.response.OrgClaim;
import com.example.authservice.model.User;
import com.example.authservice.repository.OrgMembershipRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    @Value("${app.jwtSecret}")
    private String jwtSecret;

    @Value("${app.jwtExpirationInMs}")
    private int jwtExpirationInMs;

    private final OrgMembershipRepository orgMembershipRepository;

    public String generateToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationInMs);

        List<OrgClaim> orgClaims = orgMembershipRepository.findByUser(user).stream()
                .map(membership -> new OrgClaim(membership.getOrganization().getId(), membership.getRole()))
                .collect(Collectors.toList());

        Claims claims = Jwts.claims().setSubject(user.getId().toString());
        claims.put("email", user.getEmail());
        claims.put("orgs", orgClaims);
        claims.put("mfa", user.isMfaEnabled());

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(SignatureAlgorithm.HS512, jwtSecret)
                .compact();
    }

    public UUID getUserIdFromJWT(String token) {
        Claims claims = Jwts.parser()
                .setSigningKey(jwtSecret)
                .parseClaimsJws(token)
                .getBody();

        return UUID.fromString(claims.getSubject());
    }

    public String getEmailFromJWT(String token) {
        Claims claims = Jwts.parser()
                .setSigningKey(jwtSecret)
                .parseClaimsJws(token)
                .getBody();

        return claims.get("email", String.class);
    }

    public boolean validateToken(String authToken) {
        try {
            Jwts.parser().setSigningKey(jwtSecret).parseClaimsJws(authToken);
            return true;
        } catch (Exception ex) {
            // It's good practice to log the specific exception (e.g., SignatureException, ExpiredJwtException)
        }
        return false;
    }
}
