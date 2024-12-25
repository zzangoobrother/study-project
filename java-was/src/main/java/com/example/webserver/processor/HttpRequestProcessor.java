package com.example.webserver.processor;

import com.example.api.Request;
import com.example.api.Response;
import com.example.application.processor.HttpRequestDispatcher;
import com.example.webserver.authorization.AuthorizationContextHolder;
import com.example.webserver.authorization.SecurePathManager;
import com.example.webserver.exception.BadRequestException;
import com.example.webserver.http.HttpResponse;
import com.example.webserver.http.HttpStatus;
import com.example.webserver.http.HttpVersion;
import com.example.webserver.middleware.MiddleWareChain;
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

        try {
            Request httpRequest = httpRequestParser.parseRequest(inputStream);
            Response httpResponse = new HttpResponse(HttpVersion.HTTP_1_1);
            forwarding(clientSocket, httpRequest, httpResponse);
        } catch (BadRequestException e) {
            log.error("Processor : {}", e.getMessage());
            httpResponseWriter.writeResponse(clientSocket, HttpResponse.badRequestOf());
        } finally {
            AuthorizationContextHolder.clearContext();
        }
    }

    private void forwarding(Socket clientSocket, Request request, Response response) throws IOException {
        middleWareChain.applyMiddleWares(request, response);

        if (SecurePathManager.isSecurePath(request.getPath(), request.getMethod()) && !AuthorizationContextHolder.isAuthorized()) {
            httpResponseWriter.writeResponse(clientSocket, HttpResponse.unauthorizedOf(""));
            return;
        }

        try {
            httpRequestDispatcher.handleRequest(request, response);
            httpResponseWriter.writeResponse(clientSocket, response);
        } catch (Exception e) {
            log.error("Processor : {}", e.getMessage());
            switch (response.getHttpStatus()) {
                case NOT_FOUND -> httpResponseWriter.writeResponse(clientSocket, HttpResponse.notFoundOf(request.getPath().getBasePath()));
                case INTERNAL_SERVER_ERROR -> httpResponseWriter.writeResponse(clientSocket, HttpResponse.internalServerErrorOf(request.getPath().getBasePath()));
                default -> {
                    response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
                    httpResponseWriter.writeResponse(clientSocket, response);
                }
            }
        }
    }
}
