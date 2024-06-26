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
                .route("path_route_hello", r -> r.path("/gateway-hello")
                        .filters(f -> f.rewritePath("/gateway-hello", "/microwervice-hello"))
                        .uri(MICROSERVICE_HOST_8080))
                .route("add-header-route", r -> r.path("/get")
                        .filters(f -> f.addRequestHeader("role", "hello-api"))
                        .uri(MICROSERVICE_HOST_8080))
                .build();
    }
}
