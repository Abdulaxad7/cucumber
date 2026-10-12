package com.example.authservice.service.impl;

import com.example.authservice.configuration.JwtProperties;
import com.example.authservice.enums.OrganizationRole;
import com.example.authservice.model.OrgMembership;
import com.example.authservice.model.Organization;
import com.example.authservice.model.User;
import com.example.authservice.repository.OrgMembershipRepository;
import com.example.authservice.support.TestJwt;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests of token issuing (unchanged, AC-2.3) and the HS512-only validation rule (AC-3.1).
 */
class JwtTokenProviderTest {

    private static final long ONE_DAY_MS = 86_400_000L;
    private static final UUID ANA_ID = UUID.fromString("7f1c2a9e-0000-4000-8000-000000000001");
    private static final UUID BEN_ID = UUID.fromString("7f1c2a9e-0000-4000-8000-000000000002");
    private static final String ANA = "ana@example.com";

    private final OrgMembershipRepository orgMembershipRepository = mock(OrgMembershipRepository.class);
    private JwtTokenProvider provider;

    @BeforeEach
    void createProvider() {
        provider = new JwtTokenProvider(new JwtProperties(TestJwt.SECRET, ONE_DAY_MS), orgMembershipRepository);
    }

    @Test
    @Tag("AC-2.3")
    void anIssuedTokenIsHs512WithTheAgreedClaims() {
        // Given
        User ana = User.builder().id(ANA_ID).email(ANA).mfaEnabled(false).build();
        UUID orgId = UUID.randomUUID();
        OrgMembership membership = new OrgMembership();
        membership.setOrganization(Organization.builder().id(orgId).build());
        membership.setRole(OrganizationRole.OWNER);
        when(orgMembershipRepository.findByUser(ana)).thenReturn(List.of(membership));

        // When
        String token = provider.generateToken(ana);

        // Then
        var jws = Jwts.parser().setSigningKey(TestJwt.key()).build().parseClaimsJws(token);
        assertThat(jws.getHeader().getAlgorithm()).isEqualTo("HS512");
        Claims claims = jws.getBody();
        assertThat(claims.getSubject()).isEqualTo(ANA_ID.toString());
        assertThat(claims.get("email", String.class)).isEqualTo(ANA);
        assertThat(claims.get("mfa", Boolean.class)).isFalse();
        assertThat(claims.get("orgs", List.class)).containsExactly(Map.of("orgId", orgId.toString(), "role", "OWNER"));
        assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime()).isEqualTo(ONE_DAY_MS);
        assertThat(provider.getUserIdFromJWT(token)).isEqualTo(ANA_ID);
        assertThat(provider.getEmailFromJWT(token)).isEqualTo(ANA);
        assertThat(provider.validateToken(token)).isTrue();
    }

    static Stream<Arguments> invalidTokens() {
        return Stream.of(
                Arguments.of("not a JWT", (Supplier<String>) () -> "not-a-jwt"),
                Arguments.of("signed with another secret", (Supplier<String>) () ->
                        TestJwt.signedWithAnotherSecret(ANA_ID, ANA)),
                Arguments.of("expired", (Supplier<String>) () -> TestJwt.expired(ANA_ID, ANA)),
                Arguments.of("unsigned", (Supplier<String>) () -> TestJwt.unsigned(ANA_ID, ANA)),
                Arguments.of("HS256 with the shared secret", (Supplier<String>) () -> TestJwt.signedWithHs256(ANA_ID, ANA)),
                Arguments.of("payload altered after signing", (Supplier<String>) () ->
                        TestJwt.withAlteredSubject(ANA_ID, ANA, BEN_ID)),
                Arguments.of("subject is not a UUID", (Supplier<String>) () -> TestJwt.withSubject("ana", ANA)),
                Arguments.of("no subject", (Supplier<String>) () -> TestJwt.withoutSubject(ANA)),
                Arguments.of("no exp", (Supplier<String>) () -> Jwts.builder().setSubject(ANA_ID.toString())
                        .signWith(TestJwt.key(), io.jsonwebtoken.SignatureAlgorithm.HS512).compact()),
                Arguments.of("empty", (Supplier<String>) () -> ""));
    }

    @ParameterizedTest(name = "AC-3.1 rejects a token: {0}")
    @MethodSource("invalidTokens")
    @Tag("AC-3.1")
    void aTokenTheGatewayWouldRejectIsRejected(String row, Supplier<String> token) {
        String value = token.get();

        assertThat(catchThrowable(() -> provider.getUserIdFromJWT(value)))
                .isInstanceOfAny(JwtException.class, IllegalArgumentException.class);
        assertThat(provider.validateToken(value)).isFalse();
    }

    static Stream<Arguments> unusableSecrets() {
        return Stream.of(
                Arguments.of("not Base64", "not base64 !!", "must be standard Base64"),
                Arguments.of("Base64 of 63 bytes", Base64.getEncoder().encodeToString(new byte[63]),
                        "must decode to at least 64 bytes"),
                Arguments.of("an unresolved placeholder", "${JWT_SECRET}", "must be set"));
    }

    @ParameterizedTest(name = "AC-3.9 refuses a secret that is {0}")
    @MethodSource("unusableSecrets")
    @Tag("AC-3.9")
    void anUnusableSecretIsRefusedWithoutShowingIt(String row, String secret, String reason) {
        assertThatThrownBy(() -> new JwtTokenProvider(new JwtProperties(secret, ONE_DAY_MS), orgMembershipRepository))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.jwtSecret")
                .hasMessageContaining(reason)
                .hasNoCause()
                .message().doesNotContain(secret);
    }

    @Test
    void aSecretOfExactly64BytesIsAccepted() {
        String secret = Base64.getEncoder().encodeToString(new byte[64]);

        assertThat(new JwtTokenProvider(new JwtProperties(secret, ONE_DAY_MS), orgMembershipRepository)).isNotNull();
    }

    @Test
    void thePropertiesNeverShowTheSecret() {
        assertThat(new JwtProperties(TestJwt.SECRET, ONE_DAY_MS).toString()).doesNotContain(TestJwt.SECRET);
    }
}
