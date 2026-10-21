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
@Constraint(validatedBy = VisibleText.Validator.class)
public @interface VisibleText {

    String message() default "must contain a visible character";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<VisibleText, CharSequence> {

        @Override
        public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
            if (value == null || value.toString().isBlank()) {
                return true;
            }
            return !value.codePoints().allMatch(Validator::isInvisible);
        }

        private static boolean isInvisible(int codePoint) {
            return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)
                    || codePoint == 0x200B || codePoint == 0x200C || codePoint == 0x200D || codePoint == 0x2060
                    || codePoint == 0xFEFF;
        }
    }
}
