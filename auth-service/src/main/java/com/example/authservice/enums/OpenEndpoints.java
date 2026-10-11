package com.example.authservice.enums;

import lombok.Getter;

@Getter
public enum OpenEndpoints {
    AUTHENTICATION("/api/v1/auth/**"),
    SWAGGER_UI("/swagger-ui/**"),
    SWAGGER_DOCS("/v3/api-docs/**"),
    ACTUATOR("/actuator/**"),
    H2_CONSOLE("/h2-console/**");

    private final String url;

    OpenEndpoints(String name) {
        this.url = name;
    }

}
