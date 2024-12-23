package com.example.application.domain.user.handler;

import com.example.api.Request;
import com.example.api.Response;
import com.example.application.domain.user.model.User;
import com.example.application.domain.user.request.LoginRequest;
import com.example.application.handler.ApiRequestHandler;
import com.example.application.processor.ArgumentResolver;
import com.example.webserver.http.HttpStatus;
import com.example.webserver.http.Session;
import com.example.webserver.http.header.HeaderConstants;
import com.example.webserver.middleware.SessionDatabase;

public class LoginRequestHandler extends ApiRequestHandler<LoginRequest, User> {

    private final ArgumentResolver<LoginRequest> argumentResolver;

    public LoginRequestHandler(ArgumentResolver<LoginRequest> argumentResolver) {
        this.argumentResolver = argumentResolver;
    }

    @Override
    public LoginRequest resolveArgument(Request httpRequest) {
        return argumentResolver.resolve(httpRequest);
    }

    @Override
    public void afterHandle(LoginRequest request, User response, Request httpRequest, Response httpResponse) {
        Session session = SessionDatabase.save(response.getUserId());

        httpResponse.setStatus(HttpStatus.FOUND);
        httpResponse.setHeader(HeaderConstants.SET_COOKIE, "sid = " + session.getSessionId() + "; Path=/ ; Max-Age=" + session.getTimeout());
        httpResponse.setHeader(HeaderConstants.LOCATION, "/");
    }

    @Override
    public void applyExceptionHandler(RuntimeException e, Response httpResponse) {
        httpResponse.setStatus(HttpStatus.UNAUTHORIZED);
    }
}
