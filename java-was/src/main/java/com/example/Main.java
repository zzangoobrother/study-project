package com.example;

import com.example.handler.ConnectionHandler;
import com.example.handler.HttpRequestHandler;
import com.example.handler.HttpResponseHandler;
import com.example.handler.ResourceHandler;
import com.example.http.HttpResponseSerializer;
import com.example.server.ServerInitializer;

public class Main {

    public static void main(String[] args) {
        ServerInitializer serverInitializer = new ServerInitializer();
        HttpRequestHandler httpRequestHandler = new HttpRequestHandler();
        ResourceHandler resourceHandler = new ResourceHandler();
        HttpResponseSerializer httpResponseSerializer = new HttpResponseSerializer();
        HttpResponseHandler httpResponseHandler = new HttpResponseHandler(httpResponseSerializer);
        ConnectionHandler connectionHandler = new ConnectionHandler(httpRequestHandler, httpResponseHandler, resourceHandler);

        try {
            serverInitializer.startServer(8080, connectionHandler);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
