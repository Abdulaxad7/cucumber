package com.example.authservice.util;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests of the D12 email rule (E49): trim, then lower-case with {@link Locale#ROOT}.
 */
class EmailNormalizerTest {

    @Test
    void asciiUpperCaseIsLowered() {
        assertThat(EmailNormalizer.normalize("Fay.Lin@Example.COM")).isEqualTo("fay.lin@example.com");
    }

    @Test
    void surroundingWhitespaceIsRemoved() {
        assertThat(EmailNormalizer.normalize(" \t fay@example.com \n")).isEqualTo("fay@example.com");
    }

    @Test
    void anAlreadyNormalEmailIsUnchanged() {
        assertThat(EmailNormalizer.normalize("fay@example.com")).isEqualTo("fay@example.com");
    }

    @Test
    void theCapitalIIsLoweredWithTheRootLocaleWhateverTheDefaultLocale() {
        // Given: Turkish lower-cases "I" to the dotless "ı" (U+0131)
        Locale defaultLocale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            // When
            String normalized = EmailNormalizer.normalize("IVY@EXAMPLE.COM");

            // Then
            assertThat(normalized).isEqualTo("ivy@example.com").doesNotContain("ı");
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }
}
