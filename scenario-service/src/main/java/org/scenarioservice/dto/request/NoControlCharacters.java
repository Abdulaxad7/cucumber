package org.scenarioservice.dto.request;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * No control character (rule T1, D19 #1): U+0000–U+001F and U+007F–U+009F ({@link Character#isISOControl(int)}).
 * With {@code trimmed}, the rule applies to the value without Unicode-blank characters at either end (D19 #9), so a
 * trailing line break in a message is fine and an inner one isn't. Null is valid.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NoControlCharacters.Validator.class)
public @interface NoControlCharacters {

    boolean trimmed() default false;

    String message() default "must not contain control characters";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<NoControlCharacters, CharSequence> {

        private boolean trimmed;

        @Override
        public void initialize(NoControlCharacters constraint) {
            trimmed = constraint.trimmed();
        }

        @Override
        public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            String checked = trimmed ? UnicodeBlank.trim(value.toString()) : value.toString();
            return checked.codePoints().noneMatch(Character::isISOControl);
        }
    }
}
