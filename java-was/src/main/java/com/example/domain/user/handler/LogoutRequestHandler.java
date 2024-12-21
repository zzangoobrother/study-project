package com.example.domain.user.handler;

import com.example.authorization.AuthorizationContext;
import com.example.authorization.AuthorizationContextHolder;
import com.example.database.SessionDatabase;
import com.example.handler.ApiRequestHandler;
import com.example.http.HttpRequest;
import com.example.http.HttpResponse;
import com.example.http.HttpStatus;
import com.example.http.Session;

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
