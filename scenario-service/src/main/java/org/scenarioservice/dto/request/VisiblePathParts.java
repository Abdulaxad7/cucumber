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
 * No invisible path parts (D10, D20 #4, AC-1.16): the path doesn't start with a Unicode-blank character, no segment
 * is made only of Unicode-blank characters, and neither is the file name before {@code .feature}. A segment that
 * merely contains a blank character is fine; empty segments are the path syntax rule's business. Null is valid.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = VisiblePathParts.Validator.class)
public @interface VisiblePathParts {

    String message() default "must not start with, or have a part made only of, invisible characters";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<VisiblePathParts, CharSequence> {

        private static final String SUFFIX = ".feature";

        @Override
        public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
            if (value == null || value.isEmpty()) {
                return true;
            }
            String path = value.toString();
            if (UnicodeBlank.isBlank(path.codePointAt(0))) {
                return false;
            }
            String[] segments = path.split("/", -1);
            for (String segment : segments) {
                if (!segment.isEmpty() && UnicodeBlank.isBlank(segment)) {
                    return false;
                }
            }
            String last = segments[segments.length - 1];
            if (last.endsWith(SUFFIX)) {
                String name = last.substring(0, last.length() - SUFFIX.length());
                return name.isEmpty() || !UnicodeBlank.isBlank(name);
            }
            return true;
        }
    }
}
