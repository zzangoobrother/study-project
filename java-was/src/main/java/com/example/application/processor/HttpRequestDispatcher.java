package com.example.application.processor;

import com.example.api.Dispatcher;
import com.example.api.Request;
import com.example.api.Response;
import com.example.application.handler.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class HttpRequestDispatcher implements Dispatcher {
    private static final Logger log = LoggerFactory.getLogger(HttpRequestDispatcher.class);

    private final HttpHandler<?, ?> defaultHandler;
    private final HandlerRegistry handlerRegistry;

    public HttpRequestDispatcher(HttpHandler<?, ?> defaultHandler, HandlerRegistry handlerRegistry) {
        this.defaultHandler = defaultHandler;
        this.handlerRegistry = handlerRegistry;
    }

    @Override
    public void handleRequest(Request request, Response response) throws IOException {
        HandlerMapping<?, ?> mapping = handlerRegistry.getHandler(request.getMethod(), request.getPath());

        if (mapping != null) {
            handleRequestWithMapping(request, response, mapping);
            return;
        }

        handleRequestWithDefaultHandler(request, response);
    }

    private <T, R> void handleRequestWithMapping(Request request, Response response, HandlerMapping<T, R> mapping) throws IOException {
        HttpHandler<T, R> handlerAdapter = mapping.getHandler();
        Triggerable<T, R> triggerable = mapping.getTriggerable();
        handlerAdapter.handle(request, response, triggerable);
    }

    private void handleRequestWithDefaultHandler(Request request, Response response) throws IOException {
        defaultHandler.handle(request, response, null);
    }
}
