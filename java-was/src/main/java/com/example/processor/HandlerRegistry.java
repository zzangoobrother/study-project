package com.example.processor;

import com.example.handler.HttpHandler;
import com.example.http.HttpMethod;
import com.example.http.Path;

import java.util.List;
import java.util.regex.Matcher;

public class HandlerRegistry {

    private final List<HandlerMapping<?, ?>> handlerMappings;

    public HandlerRegistry(List<HandlerMapping<?, ?>> handlerMappings) {
        this.handlerMappings = handlerMappings;
    }

    public <T, R> void registerHandler(HttpMethod httpMethod, String url, HttpHandler<T, R> handler, Triggerable<T, R> triggerable) {
        handlerMappings.add(new HandlerMapping<>(httpMethod, url, handler, triggerable));
    }

    public HandlerMapping<?, ?> getHandler(HttpMethod method, Path path) {
        for (HandlerMapping<?, ?> mapping : handlerMappings) {
            Matcher matcher = mapping.getPattern().matcher(path.getBasePath());
            if (matcher.matches() && mapping.getHttpMethod() == method) {
                return mapping;
            }
        }

        return null;
    }
}
