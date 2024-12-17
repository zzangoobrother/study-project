package com.example.processor;

import com.example.http.HttpRequest;
import com.example.http.HttpResponse;
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

    public void process(Socket clientSocket) throws IOException {
        InputStream inputStream = clientSocket.getInputStream();
        HttpRequest httpRequest = httpRequestParser.parseRequest(inputStream);
        HttpResponse httpResponse = new HttpResponse(HttpVersion.HTTP_1_1);

        middleWareChain.applyMiddleWares(httpRequest, httpResponse);

        if (Securep)
    }
}
