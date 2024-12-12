package com.example.processor;

import com.example.handler.HttpHandler;
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
    private final HttpResponseWriter httpResponseWriter;
    private final HttpHandler httpHandler;

    public HttpRequestDispatcher(HttpRequestBuilder httpRequestBuilder, HttpResponseWriter httpResponseWriter, HttpHandler httpHandler) {
        this.httpRequestBuilder = httpRequestBuilder;
        this.httpResponseWriter = httpResponseWriter;
        this.httpHandler = httpHandler;
    }

    public void handleConnection(final Socket clientSocket) throws IOException {
        InputStream requestStream = clientSocket.getInputStream();
        HttpRequest httpRequest = httpRequestBuilder.parseRequest(requestStream);
        HttpResponse httpResponse = new HttpResponse(HttpVersion.HTTP_1_1);

        try {
            httpHandler.handle(httpRequest, httpResponse);
        } catch (Exception e) {
            log.error("File not found! : {}", httpRequest.getPath());
            httpResponseWriter.writeResponse(clientSocket, HttpResponse.notFoundOf(httpRequest.getPath().getValue()));
            return;
        }

        httpResponseWriter.writeResponse(clientSocket, httpResponse);
    }
}
