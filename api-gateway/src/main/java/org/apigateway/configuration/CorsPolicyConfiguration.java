package org.apigateway.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.server.mvc.filter.HttpHeadersFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CorsProperties.class)
public class CorsPolicyConfiguration {

    private static final String ALL_PATHS = "/**";
    private static final String CORS_HEADER_PREFIX = "Access-Control-";

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration policy = new CorsConfiguration();
        policy.setAllowedOrigins(properties.allowedOrigins());
        policy.setAllowedMethods(properties.allowedMethods());
        policy.setAllowedHeaders(properties.allowedHeaders());
        policy.setExposedHeaders(properties.exposedHeaders());
        policy.setAllowCredentials(false);
        policy.setMaxAge(properties.maxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(ALL_PATHS, policy);
        return source;
    }

    @Bean
    public HttpHeadersFilter.ResponseHttpHeadersFilter downstreamCorsHeadersFilter() {
        return (downstreamHeaders, response) -> {
            HttpHeaders filtered = new HttpHeaders();
            downstreamHeaders.forEach((name, values) -> {
                if (!name.regionMatches(true, 0, CORS_HEADER_PREFIX, 0, CORS_HEADER_PREFIX.length())) {
                    filtered.addAll(name, values);
                }
            });
            return filtered;
        };
    }
}
