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
import java.nio.charset.StandardCharsets;

/**
 * The annotated string is at most {@link #value()} bytes long in UTF-8 (A6). BCrypt only accepts passwords of at most
 * 72 bytes, so a character count isn't enough. {@code null} is valid; combine with {@code @NotBlank} to require a value.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MaxUtf8Bytes.Validator.class)
public @interface MaxUtf8Bytes {

    int value();

    String message() default "must be at most {value} bytes long in UTF-8";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<MaxUtf8Bytes, CharSequence> {

        private int maxBytes;

        @Override
        public void initialize(MaxUtf8Bytes constraint) {
            maxBytes = constraint.value();
        }

        @Override
        public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
            return value == null || value.toString().getBytes(StandardCharsets.UTF_8).length <= maxBytes;
        }
    }
}
