package com.example.processor;

import com.example.handler.HttpHandler;
import com.example.http.HttpRequest;
import com.example.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpRequestDispatcher {
    private static final Logger log = LoggerFactory.getLogger(HttpRequestDispatcher.class);

    private final HttpHandler<?, ?> defaultHandler;
    private final HandlerRegistry handlerRegistry;

    public HttpRequestDispatcher(HttpHandler<?, ?> defaultHandler, HandlerRegistry handlerRegistry) {
        this.defaultHandler = defaultHandler;
        this.handlerRegistry = handlerRegistry;
    }

    public void handleConnection(final HttpRequest request, HttpResponse response) throws Exception {
        HandlerMapping<?, ?> mapping = handlerRegistry.getHandler(request.getMethod(), request.getPath());

        if (mapping != null) {
            handleRequestWithMapping(request, response, mapping);
            return;
        }

        handleRequestWithDefaultHandler(request, response);
    }

    private <T, R> void handleRequestWithMapping(HttpRequest request, HttpResponse response, HandlerMapping<T, R> mapping) throws Exception {
        HttpHandler<T, R> handlerAdapter = mapping.getHandler();
        Triggerable<T, R> triggerable = mapping.getTriggerable();
        handlerAdapter.handle(request, response, triggerable);
    }

    private void handleRequestWithDefaultHandler(HttpRequest request, HttpResponse response) throws Exception {
        defaultHandler.handle(request, response, null);
    }
}
