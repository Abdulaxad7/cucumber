package org.apigateway.security;

import org.apigateway.support.TestTokens;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class JwtSecretDecoderTest {

    @Test
    void aStandardBase64SecretOfAtLeast64BytesBecomesAnHs512Key() {
        // When
        SecretKey key = JwtSecretDecoder.decode(TestTokens.SECRET);

        // Then
        assertThat(key.getAlgorithm()).isEqualTo("HmacSHA512");
        assertThat(key.getEncoded()).isEqualTo(Base64.getDecoder().decode(TestTokens.SECRET));
    }

    @Test
    @Tag("AC-8.3")
    void aSecretThatIsNotStandardBase64IsRefusedWithoutRevealingIt() {
        // Given
        String secret = "some-secret-key-that-is-long-enough";

        // When / Then
        assertThatIllegalStateException()
                .isThrownBy(() -> JwtSecretDecoder.decode(secret))
                .withMessageContaining("JWT_SECRET")
                .withMessageNotContaining(secret)
                .withNoCause();
    }

    @Test
    @Tag("AC-8.3")
    void aSecretShorterThan64BytesIsRefusedWithoutRevealingIt() {
        // Given
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String secret = Base64.getEncoder().encodeToString(bytes);

        // When / Then
        assertThatIllegalStateException()
                .isThrownBy(() -> JwtSecretDecoder.decode(secret))
                .withMessageContaining("JWT_SECRET")
                .withMessageContaining("64 bytes")
                .withMessageNotContaining(secret);
    }
}
