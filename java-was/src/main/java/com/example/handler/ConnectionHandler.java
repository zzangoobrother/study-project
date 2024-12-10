package com.example.handler;

import com.example.http.HttpRequest;
import com.example.http.HttpResponse;
import com.example.http.HttpStatus;
import com.example.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

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
        try {
            InputStream inputStream = resourceHandler.readFileAsStream(httpRequest.getPath().getValue());
            BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            StringBuilder responseBuilder = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                responseBuilder.append(line);
            }

            response = responseBuilder.toString();
        } catch (IllegalArgumentException e) {
            log.error("File not found! : {}", httpRequest.getPath());
            httpResponseHandler.writeResponse(clientSocket, HttpResponse.notFoundOf(httpRequest.getPath().getValue()));
        }

        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(responseBytes);

        HttpResponse httpResponse = HttpResponse.builder()
                .httpVersion(HttpVersion.HTTP_1_1)
                .httpStatus(HttpStatus.OK)
                .headers(Map.of("Content-Type", "text/html; charset=UTF-8"))
                .body(body)
                .build();

        httpResponseHandler.writeResponse(clientSocket, httpResponse);
    }
}
