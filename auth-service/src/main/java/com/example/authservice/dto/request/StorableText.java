package com.example.authservice.dto.request;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = StorableText.Validator.class)
public @interface StorableText {

    boolean trimmed() default false;

    String message() default "must not contain control characters or invalid Unicode characters";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<StorableText, CharSequence> {

        private boolean trimmed;

        @Override
        public void initialize(StorableText constraint) {
            trimmed = constraint.trimmed();
        }

        @Override
        public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            String stored = trimmed ? value.toString().trim() : value.toString();
            for (int i = 0; i < stored.length(); i++) {
                char c = stored.charAt(i);
                if (Character.isISOControl(c) || Character.isLowSurrogate(c)) {
                    return false;
                }
                if (Character.isHighSurrogate(c)) {
                    if (i + 1 == stored.length() || !Character.isLowSurrogate(stored.charAt(i + 1))) {
                        return false;
                    }
                    i++;
                }
            }
            return true;
        }
    }
}
