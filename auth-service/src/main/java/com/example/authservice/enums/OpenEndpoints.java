package com.example.authservice.enums;

import lombok.Getter;

@Getter
public enum OpenEndpoints {
    SWAGGER_UI("/swagger-ui/**"),
    SWAGGER_DOCS("/v3/api-docs/**");

    private final String url;

    OpenEndpoints(String name) {
        this.url = name;
    }

}
