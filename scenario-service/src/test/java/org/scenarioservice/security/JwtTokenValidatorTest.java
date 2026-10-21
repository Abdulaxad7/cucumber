package org.scenarioservice.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.scenarioservice.configuration.JwtProperties;
import org.scenarioservice.support.TestSettings;
import org.scenarioservice.support.TestTokens;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * D6 (unit): every token rule. A token is valid only when it is signed with the shared secret, its algorithm is
 * exactly HS512, {@code exp} is present and in the future, and {@code sub} is a canonical UUID. The secret must be
 * standard Base64 of at least 64 bytes, and no message ever contains the secret.
 */
class JwtTokenValidatorTest {

    private static final UUID GUS = UUID.fromString("5b0f6c9e-2a4d-4e8b-9c1f-3d7a8e2b6c40");
    private static final String EMAIL = "gus@example.com";

    private final JwtTokenValidator validator = new JwtTokenValidator(new JwtProperties(TestSettings.JWT_SECRET));

    @Test
    @Tag("AC-7.1")
    @DisplayName("D6: a valid HS512 token gives its subject")
    void aValidTokenGivesItsSubject() {
        assertThat(validator.userIdOf(TestTokens.valid(GUS, EMAIL))).isEqualTo(GUS);
    }

    @Test
    @DisplayName("D6: a canonical subject in upper case is accepted and normalised")
    void anUpperCaseCanonicalSubjectIsAccepted() {
        String token = TestTokens.withSubject(GUS.toString().toUpperCase(Locale.ROOT), EMAIL);

        assertThat(validator.userIdOf(token)).isEqualTo(GUS);
    }

    @ParameterizedTest(name = "AC-7.1 D6: the token \"{0}\" is rejected")
    @ValueSource(strings = {"expired", "other secret", "HS256", "no exp", "no sub", "sub 1-1-1-1-1",
            "sub with a space", "sub with 7-digit first group", "sub not a uuid", "alg none", "not a JWT", "empty"})
    @Tag("AC-7.1")
    void anInvalidTokenIsRejected(String kind) {
        String token = switch (kind) {
            case "expired" -> TestTokens.expired(GUS, EMAIL);
            case "other secret" -> TestTokens.signedWithAnotherSecret(GUS, EMAIL);
            case "HS256" -> TestTokens.signedWithHs256(GUS, EMAIL);
            case "no exp" -> TestTokens.withoutExpiration(GUS, EMAIL);
            case "no sub" -> TestTokens.withoutSubject(EMAIL);
            case "sub 1-1-1-1-1" -> TestTokens.withSubject("1-1-1-1-1", EMAIL);
            case "sub with a space" -> TestTokens.withSubject(" " + GUS, EMAIL);
            case "sub with 7-digit first group" -> TestTokens.withSubject(GUS.toString().substring(1), EMAIL);
            case "sub not a uuid" -> TestTokens.withSubject("gus", EMAIL);
            case "alg none" -> TestTokens.unsigned(GUS, EMAIL);
            case "not a JWT" -> "abc.def.ghi";
            default -> "";
        };

        assertThat(catchThrowable(() -> validator.userIdOf(token))).isInstanceOf(InvalidTokenException.class)
                .hasMessageNotContaining(GUS.toString()).hasMessageNotContaining(EMAIL);
    }

    @Test
    @Tag("AC-8.12")
    @DisplayName("D6: an unresolved ${JWT_SECRET} names the variable")
    void anUnresolvedSecretNamesTheVariable() {
        assertThat(catchThrowable(() -> new JwtTokenValidator(new JwtProperties("${JWT_SECRET}"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("app.jwtSecret must be set through the JWT_SECRET environment variable");
    }

    @ParameterizedTest(name = "D6: the secret \"{0}\" isn't standard Base64")
    @ValueSource(strings = {"not-base64!QA", "c2VjcmV0_c2VjcmV0-", "c2VjcmV0 c2VjcmV0"})
    @Tag("AC-8.12")
    void aSecretThatIsNotStandardBase64IsRejectedWithoutItsValue(String secret) {
        assertThat(catchThrowable(() -> new JwtTokenValidator(new JwtProperties(secret))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("app.jwtSecret must be standard Base64 (RFC 4648 section 4)")
                .hasNoCause();
    }

    @Test
    @Tag("AC-8.12")
    @DisplayName("D6: a secret of 32 bytes is rejected with the byte counts only")
    void aShortSecretIsRejectedWithTheByteCountOnly() {
        String secret = Base64.getEncoder().encodeToString(
                "thirty-two bytes of TEST secret!".getBytes(StandardCharsets.US_ASCII));

        assertThat(catchThrowable(() -> new JwtTokenValidator(new JwtProperties(secret))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("app.jwtSecret must decode to at least 64 bytes for HS512, but it decodes to 32 bytes");
    }

    @Test
    @DisplayName("G12: the JWT settings never show the secret")
    void theJwtSettingsAreRedacted() {
        assertThat(new JwtProperties(TestSettings.JWT_SECRET).toString()).doesNotContain(TestSettings.JWT_SECRET)
                .contains("<redacted>");
    }
}
