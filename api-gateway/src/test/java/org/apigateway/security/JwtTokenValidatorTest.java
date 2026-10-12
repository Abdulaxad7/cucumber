package org.apigateway.security;

import org.apigateway.configuration.JwtProperties;
import org.apigateway.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenValidatorTest {

    private final JwtTokenValidator validator = new JwtTokenValidator(new JwtProperties(TestTokens.SECRET));

    @Test
    void aValidTokenYieldsItsSubject() {
        assertThat(validator.validate(TestTokens.valid())).hasValue(TestTokens.USER_ID);
    }

    @Test
    void aTokenSignedWithTheSharedSecretButAnotherAlgorithmIsRejected() {
        assertThat(validator.validate(TestTokens.signedWithHs256())).isEmpty();
    }

    @Test
    void aTokenWithoutExpirationIsRejected() {
        assertThat(validator.validate(TestTokens.withoutExpiration())).isEmpty();
    }

    @Test
    void anEmptyTokenIsRejected() {
        assertThat(validator.validate("")).isEmpty();
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"!", "~", "a", "user@example.com", "7f1c2a9e-0000-4000-8000-000000000001"})
    void aSubjectOfPrintableAsciiCharactersIsAccepted(String subject) {
        assertThat(validator.validate(TestTokens.withSubject(subject))).hasValue(subject);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unsafeSubjects")
    void aSubjectThatIsUnsafeForHeadersOrLogsIsRejected(String description, String subject) {
        assertThat(validator.validate(TestTokens.withSubject(subject))).isEmpty();
    }

    static Stream<Arguments> unsafeSubjects() {
        return Stream.of(
                Arguments.of("CR and LF", "abc\r\nX-Injected: 1"),
                Arguments.of("LF", "abc\nrequestId=forged"),
                Arguments.of("a space", "abc service=forged-service"),
                Arguments.of("a tab", "abc\tdef"),
                Arguments.of("DEL (U+007F)", "abc\u007F"),
                Arguments.of("a NUL character", "abc\u0000"),
                Arguments.of("a non-ASCII character", "usuário-1"),
                Arguments.of("only a space", " "));
    }

    @Test
    void theSecretIsNotPartOfThePropertiesTextForm() {
        assertThat(new JwtProperties(TestTokens.SECRET).toString()).doesNotContain(TestTokens.SECRET);
    }
}
