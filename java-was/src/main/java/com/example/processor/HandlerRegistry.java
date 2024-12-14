package com.example.processor;

import com.example.handler.HttpHandlerAdapter;
import com.example.http.HttpMethod;
import com.example.http.Path;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HandlerRegistry {

    private final List<HandlerMapping<?, ?>> handlerMappings;

    public HandlerRegistry(List<HandlerMapping<?, ?>> handlerMappings) {
        this.handlerMappings = handlerMappings;
    }

    public <T, R> void registerHandler(HttpMethod httpMethod, String url, HttpHandlerAdapter<T, R> handler, Triggerable<T, R> triggerable) {
        String regexPattern = url.replaceAll("\\{[^/]+\\}", "([^/]+)");
        handlerMappings.add(new HandlerMapping<>(httpMethod, Pattern.compile(regexPattern), handler, triggerable));
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
