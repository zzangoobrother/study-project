package com.example;

import com.example.application.database.DatabaseConfig;
import com.example.application.database.H2Console;
import com.example.application.database.dao.*;
import com.example.application.domain.comment.argumentresolver.CreateCommentArgumentResolver;
import com.example.application.domain.comment.business.CreateCommentLogic;
import com.example.application.domain.comment.handler.CreateCommentRequestHandler;
import com.example.application.domain.comment.request.CreateCommentRequest;
import com.example.application.domain.images.handler.ImageResourceHandler;
import com.example.application.domain.post.argumentresolver.PostCreateArgumentResolver;
import com.example.application.domain.post.business.GetPostListLogic;
import com.example.application.domain.post.business.PostCreateLogic;
import com.example.application.domain.post.handler.GetPostListRequestHandler;
import com.example.application.domain.post.handler.PostCreateRequestHandler;
import com.example.application.domain.post.request.PostCreateRequest;
import com.example.application.domain.user.argumentresolver.LoginArgumentResolver;
import com.example.application.domain.user.argumentresolver.RegisterArgumentResolver;
import com.example.application.domain.user.business.GetUserInfoLogic;
import com.example.application.domain.user.business.GetUserListLogic;
import com.example.application.domain.user.business.LoginUserLogic;
import com.example.application.domain.user.business.RegisterUserLogic;
import com.example.application.domain.user.handler.*;
import com.example.application.domain.user.request.LoginRequest;
import com.example.application.domain.user.request.RegisterRequest;
import com.example.application.handler.StaticResourceHandler;
import com.example.application.processor.ArgumentResolver;
import com.example.application.processor.HandlerRegistry;
import com.example.application.processor.HttpRequestDispatcher;
import com.example.webserver.authorization.SecurePathManager;
import com.example.webserver.http.HttpMethod;
import com.example.webserver.middleware.MiddleWareChain;
import com.example.webserver.middleware.SessionMiddleWare;
import com.example.webserver.processor.HttpRequestParser;
import com.example.webserver.processor.HttpRequestProcessor;
import com.example.webserver.processor.HttpResponseSerializer;
import com.example.webserver.processor.HttpResponseWriter;
import com.example.webserver.server.ServerInitializer;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

public class Main {

    public static void main(String[] args) {
        ServerInitializer serverInitializer = new ServerInitializer();
        DatabaseConfig databaseConfig = new DatabaseConfig("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1", "sa", "");

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
        LoginUserLogic loginUserLogic = new LoginUserLogic(userDao);
        ArgumentResolver<LoginRequest> loginArgumentResolver = new LoginArgumentResolver();
        LoginRequestHandler loginUserHandler = new LoginRequestHandler(loginArgumentResolver);
        handlerRegistry.registerHandler(HttpMethod.POST, "/users/login", loginUserHandler, loginUserLogic);

        // 로그아웃
        LogoutRequestHandler logoutRequestHandlerAdapter = new LogoutRequestHandler();
        handlerRegistry.registerHandler(HttpMethod.POST, "/users/logout", logoutRequestHandlerAdapter, o -> null);

        // user info
        GetUserInfoLogic getUserInfoLogic = new GetUserInfoLogic(userDao);
        GetUserInfoRequestHandler userInfoHandler = new GetUserInfoRequestHandler();
        handlerRegistry.registerHandler(HttpMethod.GET, "/api/user-info", userInfoHandler, getUserInfoLogic);

        GetUserListLogic getUserListLogic = new GetUserListLogic(userDao);
        GetUserListRequestHandler getUserListRequestHandler = new GetUserListRequestHandler();
        handlerRegistry.registerHandler(HttpMethod.GET, "/api/users", getUserListRequestHandler, getUserListLogic);

        StaticResourceHandler<Void, Void> defaultResourceHandler = new StaticResourceHandler<>();
        HttpResponseSerializer httpResponseSerializer = new HttpResponseSerializer();
        HttpResponseWriter httpResponseWriter = new HttpResponseWriter(httpResponseSerializer);

        registerImageHandler(handlerRegistry);

        registerPostApi(handlerRegistry, postDao, commentDao);

        registerCommentApi(handlerRegistry, commentDao);

        HttpRequestDispatcher httpRequestDispatcher = new HttpRequestDispatcher(defaultResourceHandler, handlerRegistry);

        MiddleWareChain middleWareChain = new MiddleWareChain();
        SessionMiddleWare sessionMiddleWare = new SessionMiddleWare();
        middleWareChain.addMiddleWare(sessionMiddleWare);

        HttpRequestProcessor httpRequestProcessor = new HttpRequestProcessor(requestParser, httpRequestDispatcher, httpResponseWriter, middleWareChain);

        SecurePathManager.addSecurePath("/api/user-info", HttpMethod.GET);
        SecurePathManager.addSecurePath("/api/users", HttpMethod.GET);
        SecurePathManager.addSecurePath("/api/posts", HttpMethod.POST);
        SecurePathManager.addSecurePath("/api/comments/{postId}", HttpMethod.POST);
        
        try {
            serverInitializer.startServer(8080, httpRequestProcessor);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void registerImageHandler(HandlerRegistry handlerRegistry) {
        ImageResourceHandler imageHandler = new ImageResourceHandler();
        handlerRegistry.registerHandler(HttpMethod.GET, "/images/{filename}", imageHandler, o -> null);
    }

    private static void registerPostApi(HandlerRegistry handlerRegistry, PostDao postDao, CommentDao commentDao) {
        PostCreateLogic postCreateLogic = new PostCreateLogic(postDao);
        ArgumentResolver<PostCreateRequest> postCreateArgumentResolver = new PostCreateArgumentResolver();
        PostCreateRequestHandler postCreateRequestHandler = new PostCreateRequestHandler(postCreateArgumentResolver);
        handlerRegistry.registerHandler(HttpMethod.POST, "/api/posts", postCreateRequestHandler, postCreateLogic);

        GetPostListLogic getPostListLogic = new GetPostListLogic(postDao, commentDao);
        GetPostListRequestHandler getPostListRequestHandler = new GetPostListRequestHandler();
        handlerRegistry.registerHandler(HttpMethod.GET, "/api/posts", getPostListRequestHandler, getPostListLogic);
    }

    private static void registerCommentApi(HandlerRegistry handlerRegistry, CommentDao commentDao) {
        CreateCommentLogic createCommentLogic = new CreateCommentLogic(commentDao);
        ArgumentResolver<CreateCommentRequest> argumentResolver = new CreateCommentArgumentResolver();
        CreateCommentRequestHandler createCommentRequestHandler = new CreateCommentRequestHandler(argumentResolver);
        handlerRegistry.registerHandler(HttpMethod.POST, "/api/comments/{postId}", createCommentRequestHandler, createCommentLogic);
    }
}
