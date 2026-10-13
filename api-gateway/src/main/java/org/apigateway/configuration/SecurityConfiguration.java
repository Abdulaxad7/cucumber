package org.apigateway.configuration;

import jakarta.servlet.DispatcherType;
import org.apigateway.filter.IdentityHeaderFilter;
import org.apigateway.security.JwtAuthenticationFilter;
import org.apigateway.security.JwtTokenValidator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.CorsProcessor;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, PublicOperationsProperties.class})
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain gatewaySecurityFilterChain(HttpSecurity http,
                                                          JwtTokenValidator tokenValidator,
                                                          PublicOperationsProperties publicOperationsProperties,
                                                          AuthenticationEntryPoint authenticationEntryPoint,
                                                          CorsConfigurationSource corsConfigurationSource,
                                                          ProblemDetailCorsProcessor corsProcessor) {
        RequestMatcher publicOperations = publicOperations(publicOperationsProperties);
        http
                .addFilter(corsFilter(corsConfigurationSource, corsProcessor))
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(authenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(publicOperations).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(tokenValidator, publicOperations),
                        AnonymousAuthenticationFilter.class)
                .addFilterAfter(new IdentityHeaderFilter(), AuthorizationFilter.class);
        return http.build();
    }

    private static CorsFilter corsFilter(CorsConfigurationSource source, CorsProcessor processor) {
        CorsFilter corsFilter = new CorsFilter(source);
        corsFilter.setCorsProcessor(processor);
        return corsFilter;
    }

    private static RequestMatcher publicOperations(PublicOperationsProperties properties) {
        PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();
        List<RequestMatcher> operations = properties.publicOperations().stream()
                .map(operation -> (RequestMatcher) paths.matcher(operation.method(), operation.path()))
                .toList();
        return new OrRequestMatcher(operations);
    }
}
