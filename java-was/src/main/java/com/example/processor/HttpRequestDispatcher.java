package com.example.processor;

import com.example.handler.HttpHandlerAdapter;
import com.example.http.HttpRequest;
import com.example.http.HttpResponse;
import com.example.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

public class HttpRequestDispatcher {
    private static final Logger log = LoggerFactory.getLogger(HttpRequestDispatcher.class);

    private final HttpRequestBuilder httpRequestBuilder;
    private final HttpHandlerAdapter<?, ?> defaultHandler;
    private final HttpResponseWriter httpResponseWriter;
    private final HandlerRegistry handlerRegistry;

    public HttpRequestDispatcher(HttpRequestBuilder httpRequestBuilder, HttpHandlerAdapter<?, ?> defaultHandler, HttpResponseWriter httpResponseWriter, HandlerRegistry handlerRegistry) {
        this.httpRequestBuilder = httpRequestBuilder;
        this.defaultHandler = defaultHandler;
        this.httpResponseWriter = httpResponseWriter;
        this.handlerRegistry = handlerRegistry;
    }

    public void handleConnection(final Socket clientSocket) throws IOException {
        InputStream requestStream = clientSocket.getInputStream();
        HttpRequest httpRequest = httpRequestBuilder.parseRequest(requestStream);
        HttpResponse httpResponse = new HttpResponse(HttpVersion.HTTP_1_1);

        try {
            HandlerMapping<?, ?> mapping = handlerRegistry.getHandler(httpRequest.getMethod(), httpRequest.getPath());

            if (mapping != null) {
                handleRequestWithMapping(httpRequest, httpResponse, mapping);
            } else {
                handleRequestWithDefaultHandler(httpRequest, httpResponse);
            }
        } catch (Exception e) {
            log.error("File not found! : {}", httpRequest.getPath());
            httpResponseWriter.writeResponse(clientSocket, HttpResponse.notFoundOf(httpRequest.getPath().getBasePath()));
            return;
        }

        httpResponseWriter.writeResponse(clientSocket, httpResponse);
    }

    private <T, R> void handleRequestWithMapping(HttpRequest request, HttpResponse response, HandlerMapping<T, R> mapping) throws Exception {
        HttpHandlerAdapter<T, R> handlerAdapter = mapping.getHandler();
        Triggerable<T, R> triggerable = mapping.getTriggerable();
        handlerAdapter.handle(request, response, triggerable);
    }

    private void handleRequestWithDefaultHandler(HttpRequest request, HttpResponse response) throws Exception {
        defaultHandler.handle(request, response, null);
    }
}
