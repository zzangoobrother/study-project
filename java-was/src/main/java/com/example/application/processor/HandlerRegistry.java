package com.example.application.processor;

import com.example.application.handler.HttpHandler;
import com.example.webserver.http.HttpMethod;
import com.example.webserver.http.Path;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HandlerRegistry {

    private final Set<String> registeredUrls = new HashSet<>();
    private final List<HandlerMapping<?, ?>> handlerMappings;

    public HandlerRegistry(List<HandlerMapping<?, ?>> handlerMappings) {
        this.handlerMappings = handlerMappings;
    }

    public <T, R> void registerHandler(HttpMethod httpMethod, String url, HttpHandler<T, R> handler, Triggerable<T, R> triggerable) {
        if (registeredUrls.contains(url + httpMethod)) {
            throw new IllegalArgumentException("이미 등록된 URL 입니다.");
        }

        registeredUrls.add(url + httpMethod);
        handlerMappings.add(new HandlerMapping<>(httpMethod, url, handler, triggerable));
    }

    public HandlerMapping<?, ?> getHandler(HttpMethod method, Path path) {
        return handlerMappings.stream()
                .filter(it -> it.matchRequest(method, path.getBasePath()))
                .findFirst()
                .orElse(null);
    }
}
