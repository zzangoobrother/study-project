package com.example.handler;

import com.example.database.SessionDatabase;
import com.example.http.HttpRequest;
import com.example.http.HttpResponse;
import com.example.http.HttpStatus;
import com.example.http.Session;
import com.example.http.header.HeaderConstants;
import com.example.model.User;
import com.example.processor.resolver.ArgumentResolver;
import com.example.web.user.request.LoginRequest;

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
