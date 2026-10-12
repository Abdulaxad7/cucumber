package org.apigateway.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds and validates the downstream settings. Routes and HTTP-client timeouts are wired in
 * {@code application.yaml} (D2), so no Java routing or client code is needed.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DownstreamProperties.class)
public class DownstreamConfiguration {
}
