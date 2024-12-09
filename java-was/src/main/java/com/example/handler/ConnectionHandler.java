package com.example.handler;

import com.example.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

public class ConnectionHandler {
    private static final Logger log = LoggerFactory.getLogger(ConnectionHandler.class);

    private final HttpRequestHandler httpRequestHandler;
    private final HttpResponseHandler httpResponseHandler;
    private final ResourceHandler resourceHandler;

    public ConnectionHandler(HttpRequestHandler httpRequestHandler, HttpResponseHandler httpResponseHandler, ResourceHandler resourceHandler) {
        this.httpRequestHandler = httpRequestHandler;
        this.httpResponseHandler = httpResponseHandler;
        this.resourceHandler = resourceHandler;
    }

    public void handleConnection(final Socket clientSocket) throws IOException {
        InputStream requestStream = clientSocket.getInputStream();
        HttpRequest httpRequest = httpRequestHandler.parseRequest(requestStream);

        String response = "";
    }
}
