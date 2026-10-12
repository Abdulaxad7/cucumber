package com.example.authservice.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers auth-service's typed, validated settings (P7).
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class PropertiesConfiguration {
}
