package com.example.helloapigateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouteConfiguration {
    private static final String MICROSERVICE_HOST_8080 = "http://localhost:8080";

    @Bean
    public RouteLocator helloRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("path_simple_hello", r -> r.path("/hello").uri(MICROSERVICE_HOST_8080))
                .build();
    }
}
