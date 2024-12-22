package com.example.application.domain.user.handler;

import com.example.webserver.middleware.SessionDatabase;
import com.example.application.handler.ApiRequestHandler;
import com.example.webserver.http.HttpRequest;
import com.example.webserver.http.HttpResponse;
import com.example.webserver.http.HttpStatus;
import com.example.webserver.http.Session;
import com.example.webserver.http.header.HeaderConstants;
import com.example.application.domain.user.model.User;
import com.example.application.processor.ArgumentResolver;
import com.example.application.domain.user.request.LoginRequest;

public class LoginRequestHandler extends ApiRequestHandler<LoginRequest, User> {

    private final ArgumentResolver<LoginRequest> argumentResolver;

    public LoginRequestHandler(ArgumentResolver<LoginRequest> argumentResolver) {
        this.argumentResolver = argumentResolver;
    }

    @Override
    public LoginRequest resolveArgument(HttpRequest httpRequest) {
        return argumentResolver.resolve(httpRequest);
    }

    @Override
    public void afterHandle(LoginRequest request, User response, HttpRequest httpRequest, HttpResponse httpResponse) {
        Session session = SessionDatabase.save(response.getUserPk());

        httpResponse.setStatus(HttpStatus.FOUND);
        httpResponse.setHttpHeaders(HeaderConstants.SET_COOKIE, "sid = " + session.getSessionId() + "; Path=/ ; Max-Age=" + session.getTimeout());
        httpResponse.setHttpHeaders(HeaderConstants.LOCATION, "/");
    }

    @Override
    public void applyExceptionHandler(RuntimeException e, HttpResponse httpResponse) {
        httpResponse.setStatus(HttpStatus.FOUND);
        httpResponse.setHttpHeaders(HeaderConstants.LOCATION, "/users/login_failed");
    }
}
