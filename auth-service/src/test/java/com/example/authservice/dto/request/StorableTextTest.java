package com.example.authservice.dto.request;

import com.example.authservice.enums.OrganizationRole;
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

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase-4 D11 rule T1 (E59): {@link StorableText} rejects control characters and lone surrogates in the stored value,
 * reports the record component as the field, and never puts the value in its message.
 */
class StorableTextTest {

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

    @ParameterizedTest(name = "AC-5.1 organization name with U+{0}")
    @ValueSource(ints = {0x0000, 0x0009, 0x000A, 0x001F, 0x007F, 0x0080, 0x0085, 0x009F})
    @Tag("AC-5.1")
    void everyC0AndC1ControlCharacterIsRejected(int codePoint) {
        String name = "Org" + Character.toString(codePoint) + "QA";

        assertThat(organizationViolations(name)).extracting(ConstraintViolation::getMessage)
                .containsExactly("Organization name must not contain control characters or invalid Unicode characters");
    }

    @ParameterizedTest(name = "AC-5.1 lone surrogate in \"{0}\"")
    @ValueSource(strings = {"\uD800", "Org \uD800", "\uDC00Org", "Org\uDBFFx", "\uDE00\uD83D"})
    @Tag("AC-5.1")
    void aLoneSurrogateIsRejected(String name) {
        assertThat(organizationViolations(name)).hasSize(1);
    }

    @ParameterizedTest(name = "AC-5.4 \"{0}\" is valid")
    @ValueSource(strings = {"Caf\u00E9 \u00DCn\u00EFc\u00F6d\u00E9", "\u65E5\u672C\u8A9E\u30C1\u30FC\u30E0",
            "\u00C9quipe \uD83D\uDE00", "e\u0301clair", "\uD83D\uDE00", "Org\u00A0QA"})
    @Tag("AC-5.4")
    void ordinaryTextIncludingSurrogatePairsIsValid(String name) {
        assertThat(organizationViolations(name)).isEmpty();
    }

    @Test
    void nullIsLeftToNotBlank() {
        assertThat(new StorableText.Validator().isValid(null, null)).isTrue();
    }

    @Test
    @Tag("AC-5.1")
    void anUntrimmedFieldRejectsAControlCharacterAtEitherEnd() {
        assertThat(organizationViolations("\tOrg QA")).hasSize(1);
        assertThat(organizationViolations("Org QA\n")).hasSize(1);
    }

    @ParameterizedTest(name = "AC-5.2 project name \"{0}\" is valid after trimming")
    @ValueSource(strings = {"\u0000Checkout", "Checkout\n", "\r\nCHECKOUT\t ", "Checkout\u001F", "\tCheckout"})
    @Tag("AC-5.2")
    void aTrimmedFieldIgnoresWhatTrimmingRemoves(String name) {
        assertThat(validator.validate(new CreateProjectRequest(name))).isEmpty();
        assertThat(validator.validate(new RenameProjectRequest(name))).isEmpty();
    }

    @ParameterizedTest(name = "AC-5.1 project name \"{0}\" is still invalid after trimming")
    @ValueSource(strings = {"Mobile\u0007App", "\u0085", "Checkout\u0085", " \uDC00 "})
    @Tag("AC-5.1")
    void aTrimmedFieldRejectsWhatTrimmingKeeps(String name) {
        assertThat(validator.validate(new CreateProjectRequest(name)))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("name");
    }

    @Test
    @Tag("AC-5.1")
    void eachAnnotatedFieldIsReportedUnderItsRecordComponentName() {
        String bad = "x\u0000y";
        assertThat(paths(validator.validate(new RegisterRequest("fay@example.com", "Secret123!", bad))))
                .containsExactly("fullName");
        assertThat(paths(validator.validate(new RegisterRequest("fay\uDC00@example.com", "Secret123!", "Fay"))))
                .containsExactly("email");
        assertThat(paths(validator.validate(new LoginRequest("ana\uD800@example.com", "Secret123!"))))
                .containsExactly("email");
        assertThat(paths(validator.validate(new InviteMemberRequest("fay\uD800@example.com",
                OrganizationRole.MEMBER)))).containsExactly("email");
        assertThat(paths(validator.validate(new AcceptInvitationRequest("Secret123!", bad))))
                .containsExactly("fullName");
        assertThat(paths(validator.validate(new CreateOrganizationRequest(bad)))).containsExactly("name");
        assertThat(paths(validator.validate(new CreateProjectRequest(bad)))).containsExactly("name");
        assertThat(paths(validator.validate(new RenameProjectRequest(bad)))).containsExactly("name");
    }

    @Test
    @Tag("AC-5.1")
    void theMessageNeverContainsTheValue() {
        assertThat(organizationViolations("Secret\u0000Org")).extracting(ConstraintViolation::getMessage)
                .allSatisfy(message -> assertThat(message).doesNotContain("Secret"));
    }

    private Set<ConstraintViolation<CreateOrganizationRequest>> organizationViolations(String name) {
        return validator.validate(new CreateOrganizationRequest(name));
    }

    private static Set<String> paths(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream().map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
