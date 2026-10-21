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
 * Text PostgreSQL can store (D8, every string field): no U+0000 and no lone surrogate (a {@code char} in
 * U+D800–U+DFFF that isn't part of a valid pair). Other control characters are allowed here; fields that forbid them
 * add {@link NoControlCharacters}. Null is valid.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = StorableText.Validator.class)
public @interface StorableText {

    String message() default "must not contain U+0000 or an unpaired surrogate";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<StorableText, CharSequence> {

        @Override
        public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c == '\u0000' || Character.isLowSurrogate(c)) {
                    return false;
                }
                if (Character.isHighSurrogate(c)) {
                    if (i + 1 == value.length() || !Character.isLowSurrogate(value.charAt(i + 1))) {
                        return false;
                    }
                    i++;
                }
            }
            return true;
        }
    }
}
