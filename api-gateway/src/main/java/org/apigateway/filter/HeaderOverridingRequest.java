package org.apigateway.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

final class HeaderOverridingRequest extends HttpServletRequestWrapper {

    private final String headerName;
    private final @Nullable String value;

    HeaderOverridingRequest(HttpServletRequest request, String headerName, @Nullable String value) {
        super(request);
        this.headerName = headerName;
        this.value = value;
    }

    @Override
    public @Nullable String getHeader(String name) {
        return isOverridden(name) ? value : super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        if (!isOverridden(name)) {
            return super.getHeaders(name);
        }
        return value == null ? Collections.emptyEnumeration() : Collections.enumeration(List.of(value));
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        List<String> names = new ArrayList<>();
        Enumeration<String> original = super.getHeaderNames();
        while (original.hasMoreElements()) {
            String name = original.nextElement();
            if (!isOverridden(name)) {
                names.add(name);
            }
        }
        if (value != null) {
            names.add(headerName);
        }
        return Collections.enumeration(names);
    }

    private boolean isOverridden(String name) {
        return headerName.equalsIgnoreCase(name);
    }
}
