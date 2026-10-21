package com.example.authservice.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase-4 D11 rules T2 and T3 (E75): {@link VisibleText} rejects Unicode-blank names without doubling
 * {@code @NotBlank}'s error, and {@link MaxCodePoints} counts code points.
 */
class NameConstraintsTest {

    private static final String EMOJI = "\uD83D\uDE00";

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @ParameterizedTest(name = "AC-6.1 \"{0}\" is blank")
    @ValueSource(strings = {"\u00A0", "\u2007", "\u202F", "\u200B", "\u200C", "\u200D", "\u2060", "\uFEFF",
            "\u2060\u00A0", "\u202F \u200D", " \u200B "})
    @Tag("AC-6.1")
    void aNameMadeOnlyOfInvisibleCharactersIsBlank(String name) {
        assertThat(validator.validate(new CreateOrganizationRequest(name)))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("Organization name cannot be blank");
    }

    @ParameterizedTest(name = "AC-6.1 \"{0}\" is visible")
    @ValueSource(strings = {"a\u200B", "\u00A0Checkout", "e\u0301", "\uD83D\uDE00"})
    @Tag("AC-6.1")
    void aNameWithOneVisibleCharacterIsNotBlank(String name) {
        assertThat(validator.validate(new CreateOrganizationRequest(name))).isEmpty();
    }

    @ParameterizedTest(name = "a Java-blank \"{0}\" gets exactly one error, from @NotBlank")
    @ValueSource(strings = {"", " ", "\t", "\u3000", "\u2028"})
    void aJavaBlankNameGetsExactlyOneError(String name) {
        assertThat(validator.validate(new CreateProjectRequest(name))).hasSize(1);
        assertThat(new VisibleText.Validator().isValid(name, null)).isTrue();
        assertThat(new VisibleText.Validator().isValid(null, null)).isTrue();
    }

    @Test
    @Tag("AC-6.2")
    void nameLimitsCountCodePoints() {
        assertThat(validator.validate(new CreateOrganizationRequest(EMOJI.repeat(100)))).isEmpty();
        assertThat(validator.validate(new CreateOrganizationRequest(EMOJI.repeat(101))))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("Organization name must be at most 100 characters long");
        assertThat(validator.validate(new RenameProjectRequest(" " + EMOJI.repeat(100)))).hasSize(1);
        assertThat(validator.validate(new RegisterRequest("fay@example.com", "Secret123!", "\u00E9".repeat(101))))
                .extracting(violation -> violation.getPropertyPath().toString()).containsExactly("fullName");
        assertThat(validator.validate(new AcceptInvitationRequest("Secret123!", "a".repeat(100)))).isEmpty();
    }
}
