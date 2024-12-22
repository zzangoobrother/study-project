package com.example.application.domain.user.handler;

import com.example.webserver.authorization.AuthorizationContext;
import com.example.webserver.authorization.AuthorizationContextHolder;
import com.example.webserver.middleware.SessionDatabase;
import com.example.application.handler.ApiRequestHandler;
import com.example.webserver.http.HttpRequest;
import com.example.webserver.http.HttpResponse;
import com.example.webserver.http.HttpStatus;
import com.example.webserver.http.Session;

public class LogoutRequestHandler extends ApiRequestHandler<Void, Void> {
    @Override
    public Void resolveArgument(HttpRequest httpRequest) {
        return null;
    }

    @Override
    public void afterHandle(Void request, Void response, HttpRequest httpRequest, HttpResponse httpResponse) {
        if (!AuthorizationContextHolder.isAuthorized()) {
            httpResponse.setStatus(HttpStatus.BAD_REQUEST);
            return;
        }

        AuthorizationContext context = AuthorizationContextHolder.getContextHolder();
        Session session = context.getSession();
        SessionDatabase.delete(session.getSessionId());
        httpResponse.setStatus(HttpStatus.OK);
    }

    @Override
    public void applyExceptionHandler(RuntimeException e, HttpResponse httpResponse) {
        httpResponse.setStatus(HttpStatus.BAD_REQUEST);
    }
}
