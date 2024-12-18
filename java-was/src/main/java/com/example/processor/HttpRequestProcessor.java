package com.example.processor;

import com.example.authorization.AuthorizationContextHolder;
import com.example.authorization.SecurePathManager;
import com.example.http.HttpRequest;
import com.example.http.HttpResponse;
import com.example.http.HttpStatus;
import com.example.http.HttpVersion;
import com.example.middleware.MiddleWareChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

public class HttpRequestProcessor {
    private static final Logger log = LoggerFactory.getLogger(HttpRequestProcessor.class);

    private final HttpRequestParser httpRequestParser;
    private final HttpRequestDispatcher httpRequestDispatcher;
    private final HttpResponseWriter httpResponseWriter;
    private final MiddleWareChain middleWareChain;

    public HttpRequestProcessor(HttpRequestParser httpRequestParser, HttpRequestDispatcher httpRequestDispatcher, HttpResponseWriter httpResponseWriter, MiddleWareChain middleWareChain) {
        this.httpRequestParser = httpRequestParser;
        this.httpRequestDispatcher = httpRequestDispatcher;
        this.httpResponseWriter = httpResponseWriter;
        this.middleWareChain = middleWareChain;
    }

    public void process(Socket clientSocket) throws IOException {
        InputStream inputStream = clientSocket.getInputStream();
        HttpRequest httpRequest = httpRequestParser.parseRequest(inputStream);
        HttpResponse httpResponse = new HttpResponse(HttpVersion.HTTP_1_1);

        middleWareChain.applyMiddleWares(httpRequest, httpResponse);

        if (SecurePathManager.isSecurePath(httpRequest.getPath(), httpRequest.getMethod()) && !AuthorizationContextHolder.isAuthorized()) {
            httpResponseWriter.writeResponse(clientSocket, HttpResponse.unauthorizedOf(httpRequest.getPath().getBasePath()));
            return;
        }

        try {
            httpRequestDispatcher.handleConnection(httpRequest, httpResponse);
            httpResponseWriter.writeResponse(clientSocket, httpResponse);
        } catch (Exception e) {
            log.error("Processor : {}", e.getMessage());
            switch (httpResponse.getHttpStatus()) {
                case NOT_FOUND -> httpResponseWriter.writeResponse(clientSocket, HttpResponse.notFoundOf(httpRequest.getPath().getBasePath()));
                case INTERNAL_SERVER_ERROR -> httpResponseWriter.writeResponse(clientSocket, HttpResponse.internalServerErrorOf(httpRequest.getPath().getBasePath()));
                default -> {
                    httpResponse.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
                    httpResponseWriter.writeResponse(clientSocket, httpResponse);
                }
            }
        }
    }
}
