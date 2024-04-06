package com.example.helloapigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class OrderApiRouteLocator {

    @Value("${route.order-api.v1.base-url}")
    private String orderBaseUrl;

    private static final String GATEWAYPATH = "/providers/order-api/v1/";

    @Bean
    public RouteLocator customOrderApiRouteLocator(RouteLocatorBuilder routeLocatorBuilder) {
        return routeLocatorBuilder.routes()
                .route("order-api",
                        r -> r.path(GATEWAYPATH + "**")
                                .filters(f -> f.rewritePath(GATEWAYPATH + "(?<servicePath>.*)", "/${servicePath}"))
                                .uri(orderBaseUrl)
                ).build();
    } // GET http://localhost:8083/providers/order-api/v1/orders
}
