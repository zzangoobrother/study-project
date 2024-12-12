package com.example;

import com.example.handler.ResourceHandler;
import com.example.http.HttpResponseSerializer;
import com.example.processor.HttpRequestBuilder;
import com.example.processor.HttpRequestDispatcher;
import com.example.processor.HttpResponseWriter;
import com.example.server.ServerInitializer;

public class Main {

    public static void main(String[] args) {
        ServerInitializer serverInitializer = new ServerInitializer();
        HttpRequestBuilder httpRequestBuilder = new HttpRequestBuilder();
        ResourceHandler resourceHandler = new ResourceHandler();
        HttpResponseSerializer httpResponseSerializer = new HttpResponseSerializer();
        HttpResponseWriter httpResponseWriter = new HttpResponseWriter(httpResponseSerializer);
        HttpRequestDispatcher connectionHandler = new HttpRequestDispatcher(httpRequestBuilder, httpResponseWriter, resourceHandler);

        try {
            serverInitializer.startServer(8080, connectionHandler);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
