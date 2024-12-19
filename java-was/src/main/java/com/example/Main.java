package com.example;

import com.example.authorization.SecurePathManager;
import com.example.database.DatabaseConfig;
import com.example.database.H2Console;
import com.example.database.dao.*;
import com.example.handler.*;
import com.example.http.HttpMethod;
import com.example.http.HttpResponseSerializer;
import com.example.middleware.MiddleWareChain;
import com.example.middleware.SessionMiddleWare;
import com.example.model.business.LoginUserLogic;
import com.example.model.business.RegisterUserLogic;
import com.example.model.business.user.GetUserInfoLogic;
import com.example.model.business.user.GetUserListLogic;
import com.example.processor.*;
import com.example.processor.resolver.ArgumentResolver;
import com.example.processor.resolver.LoginArgumentResolver;
import com.example.processor.resolver.RegisterArgumentResolver;
import com.example.server.ServerInitializer;
import com.example.web.user.request.LoginRequest;
import com.example.web.user.request.RegisterRequest;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

public class Main {

    public static void main(String[] args) {
        ServerInitializer serverInitializer = new ServerInitializer();
        DatabaseConfig databaseConfig = new DatabaseConfig("jdbc:h2:tcp://localhost/~/study;MODE=MYSQL", "sa", "");

        CompletableFuture.runAsync(() -> H2Console.main(databaseConfig));
        HandlerRegistry handlerRegistry = new HandlerRegistry(new ArrayList<>());
        HttpRequestParser requestParser = new HttpRequestParser();

        UserDao userDao = new UserDaoImpl(databaseConfig);

        PostDao postDao = new PostDaoImpl(databaseConfig);

        CommentDao commentDao = new CommentDaoImpl(databaseConfig);

        // 회원가입
        RegisterUserLogic registerUserLogic = new RegisterUserLogic(userDao);
        ArgumentResolver<RegisterRequest> requestArgumentResolver = new RegisterArgumentResolver();
        RegisterRequestHandler registerUserHandler = new RegisterRequestHandler(requestArgumentResolver);
        handlerRegistry.registerHandler(HttpMethod.POST, "/users/create", registerUserHandler, registerUserLogic);

        // 로그인
        LoginUserLogic loginUserLogic = new LoginUserLogic(userDatabase);
        ArgumentResolver<LoginRequest> loginArgumentResolver = new LoginArgumentResolver();
        LoginRequestHandler loginUserHandler = new LoginRequestHandler(loginArgumentResolver);
        handlerRegistry.registerHandler(HttpMethod.POST, "/users/login", loginUserHandler, loginUserLogic);

        // 로그아웃
        LogoutRequestHandler logoutRequestHandlerAdapter = new LogoutRequestHandler();
        handlerRegistry.registerHandler(HttpMethod.POST, "/users/logout", logoutRequestHandlerAdapter, o -> null);

        // user info
        GetUserInfoLogic getUserInfoLogic = new GetUserInfoLogic(userDatabase);
        GetUserInfoRequestHandler userInfoHandler = new GetUserInfoRequestHandler();
        handlerRegistry.registerHandler(HttpMethod.GET, "/api/user-info", userInfoHandler, getUserInfoLogic);

        GetUserListLogic getUserListLogic = new GetUserListLogic(userDatabase);
        GetUserListRequestHandler getUserListRequestHandler = new GetUserListRequestHandler();
        handlerRegistry.registerHandler(HttpMethod.GET, "/api/users", getUserListRequestHandler, getUserListLogic);

        ResourceHandler<Void, Void> defaultResourceHandler = new ResourceHandler<>();
        HttpResponseSerializer httpResponseSerializer = new HttpResponseSerializer();
        HttpResponseWriter httpResponseWriter = new HttpResponseWriter(httpResponseSerializer);

        HttpRequestDispatcher httpRequestDispatcher = new HttpRequestDispatcher(defaultResourceHandler, handlerRegistry);

        MiddleWareChain middleWareChain = new MiddleWareChain();
        SessionMiddleWare sessionMiddleWare = new SessionMiddleWare();
        middleWareChain.addMiddleWare(sessionMiddleWare);

        HttpRequestProcessor httpRequestProcessor = new HttpRequestProcessor(requestParser, httpRequestDispatcher, httpResponseWriter, middleWareChain);

        SecurePathManager.addSecurePath("/api/user-info", HttpMethod.GET);
        SecurePathManager.addSecurePath("/api/users", HttpMethod.GET);

        try {
            serverInitializer.startServer(8080, httpRequestProcessor);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
