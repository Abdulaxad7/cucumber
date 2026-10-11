package org.router;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRouter {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder){

    }
}
