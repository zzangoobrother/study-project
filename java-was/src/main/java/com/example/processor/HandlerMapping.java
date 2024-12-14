package com.example.processor;

import com.example.handler.HttpHandlerAdapter;
import com.example.http.HttpMethod;

import java.util.regex.Pattern;

public class HandlerMapping<T, R> {

    private final HttpMethod httpMethod;
    private final Pattern pattern;
    private final HttpHandlerAdapter<T, R> handler;
    private final Triggerable<T, R> triggerable;

    public HandlerMapping(HttpMethod httpMethod, Pattern pattern, HttpHandlerAdapter<T, R> handler, Triggerable<T, R> triggerable) {
        this.httpMethod = httpMethod;
        this.pattern = pattern;
        this.handler = handler;
        this.triggerable = triggerable;
    }

    public Triggerable<T, R> getTriggerable() {
        return triggerable;
    }

    public Pattern getPattern() {
        return pattern;
    }

    public HttpMethod getHttpMethod() {
        return httpMethod;
    }

    public HttpHandlerAdapter<T, R> getHandler() {
        return handler;
    }
}
