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
 * The path syntax of D10 and D19 #10: relative ({@code /} doesn't start it), segments separated by {@code /}, no empty,
 * {@code .} or {@code ..} segment, no backslash, and a last segment that ends in exactly lower-case {@code .feature}
 * after at least one other character. Length, control characters, lone surrogates and invisible parts are the other
 * path constraints' business. Null and empty are valid here ({@code @NotEmpty} reports them).
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = FeaturePath.Validator.class)
public @interface FeaturePath {

    String message() default "must be a relative path of segments separated by / that ends in .feature";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<FeaturePath, CharSequence> {

        private static final String SUFFIX = ".feature";

        @Override
        public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
            if (value == null || value.isEmpty()) {
                return true;
            }
            String path = value.toString();
            if (path.startsWith("/") || path.indexOf('\\') >= 0) {
                return false;
            }
            String[] segments = path.split("/", -1);
            for (String segment : segments) {
                if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                    return false;
                }
            }
            String last = segments[segments.length - 1];
            return last.endsWith(SUFFIX) && last.length() > SUFFIX.length();
        }
    }
}
