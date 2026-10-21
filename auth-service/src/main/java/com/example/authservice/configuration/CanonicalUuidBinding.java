package com.example.authservice.configuration;

import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

import java.beans.PropertyEditorSupport;
import java.util.UUID;
import java.util.regex.Pattern;

@ControllerAdvice
public class CanonicalUuidBinding {

    @InitBinder
    public void canonicalUuids(WebDataBinder binder) {
        binder.registerCustomEditor(UUID.class, new CanonicalUuidEditor());
    }

    static final class CanonicalUuidEditor extends PropertyEditorSupport {

        private static final Pattern CANONICAL =
                Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

        @Override
        public void setAsText(String text) {
            if (text == null || text.isEmpty()) {
                setValue(null);
                return;
            }
            if (!CANONICAL.matcher(text).matches()) {
                throw new IllegalArgumentException("Not a canonical UUID");
            }
            setValue(UUID.fromString(text));
        }

        @Override
        public String getAsText() {
            Object value = getValue();
            return value == null ? "" : value.toString();
        }
    }
}
