package com.example;

import com.example.handler.ApiRequestHandlerAdapter;
import com.example.handler.HttpHandlerAdapter;
import com.example.handler.ResourceHandlerAdapter;
import com.example.http.HttpMethod;
import com.example.http.HttpResponseSerializer;
import com.example.model.business.RegisterUserLogic;
import com.example.processor.*;
import com.example.server.ServerInitializer;
import com.example.web.user.RegisterRequest;

import java.util.ArrayList;

public class Main {

    public static void main(String[] args) {
        ServerInitializer serverInitializer = new ServerInitializer();

        HandlerRegistry handlerRegistry = new HandlerRegistry(new ArrayList<>());
        HttpRequestBuilder httpRequestBuilder = new HttpRequestBuilder();

        RegisterUserLogic registerUserLogic = new RegisterUserLogic();
        ArgumentResolver<RegisterRequest> requestArgumentResolver = new RegisterArgumentResolver();
        HttpHandlerAdapter<RegisterRequest, Void> registerUserHandler = new ApiRequestHandlerAdapter<>(requestArgumentResolver);
        handlerRegistry.registerHandler(HttpMethod.POST, "/users/create", registerUserHandler, registerUserLogic);


        HttpHandlerAdapter<Void, Void> defaultResourceHandler = new ResourceHandlerAdapter<>();
        HttpResponseSerializer httpResponseSerializer = new HttpResponseSerializer();
        HttpResponseWriter httpResponseWriter = new HttpResponseWriter(httpResponseSerializer);
        HttpRequestDispatcher connectionHandler = new HttpRequestDispatcher(httpRequestBuilder, defaultResourceHandler, httpResponseWriter, handlerRegistry);

        try {
            serverInitializer.startServer(8080, connectionHandler);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
