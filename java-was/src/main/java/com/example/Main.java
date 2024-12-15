package com.example;

import com.example.database.Database;
import com.example.handler.HttpHandlerAdapter;
import com.example.handler.LoginRequestHandlerAdapter;
import com.example.handler.RegisterRequestHandlerAdapter;
import com.example.http.HttpMethod;
import com.example.http.HttpResponseSerializer;
import com.example.model.User;
import com.example.model.business.LoginUserLogic;
import com.example.model.business.RegisterUserLogic;
import com.example.processor.HandlerRegistry;
import com.example.processor.HttpRequestBuilder;
import com.example.processor.HttpRequestDispatcher;
import com.example.processor.HttpResponseWriter;
import com.example.processor.resolver.ArgumentResolver;
import com.example.processor.resolver.LoginArgumentResolver;
import com.example.processor.resolver.RegisterArgumentResolver;
import com.example.server.ServerInitializer;
import com.example.web.user.LoginRequest;
import com.example.web.user.RegisterRequest;

import java.util.ArrayList;

public class Main {

    public static void main(String[] args) {
        ServerInitializer serverInitializer = new ServerInitializer();

        HandlerRegistry handlerRegistry = new HandlerRegistry(new ArrayList<>());
        HttpRequestBuilder httpRequestBuilder = new HttpRequestBuilder();

        Database<User> userDatabase = new Database<>();

        RegisterUserLogic registerUserLogic = new RegisterUserLogic(userDatabase);
        ArgumentResolver<RegisterRequest> requestArgumentResolver = new RegisterArgumentResolver();
        RegisterRequestHandlerAdapter registerUserHandler = new RegisterRequestHandlerAdapter(requestArgumentResolver);
        handlerRegistry.registerHandler(HttpMethod.POST, "/users/create", registerUserHandler, registerUserLogic);

        LoginUserLogic loginUserLogic = new LoginUserLogic(userDatabase);
        ArgumentResolver<LoginRequest> loginArgumentResolver = new LoginArgumentResolver();
        LoginRequestHandlerAdapter loginUserHandler = new LoginRequestHandlerAdapter(loginArgumentResolver);
        handlerRegistry.registerHandler(HttpMethod.POST, "/users/create", registerUserHandler, registerUserLogic);

        HttpHandlerAdapter<Void, Void> defaultResourceHandler = new RegisterRequestHandlerAdapter<>();
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
