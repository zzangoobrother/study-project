package com.example.middleware;

import com.example.authorization.AuthorizationContext;
import com.example.authorization.AuthorizationContextHolder;
import com.example.database.SessionDatabase;
import com.example.http.HttpRequest;
import com.example.http.HttpResponse;
import com.example.http.Session;
import com.example.http.header.HeaderConstants;
import com.example.http.header.HttpHeaders;

import java.util.Collections;
import java.util.Optional;

public class SessionMiddleWare implements MiddleWare {
    @Override
    public void applyMiddleWare(HttpRequest request, HttpResponse response) {
        HttpHeaders httpHeaders = request.getHttpHeaders();

        try {
            Optional<String> optionalCookie = httpHeaders.getSubValueOfHeader(HeaderConstants.COOKIE, "sid");
            if (optionalCookie.isPresent()) {
                String cookie = optionalCookie.get();
                if (SessionDatabase.containsKey(cookie)) {
                    Session session = SessionDatabase.find(cookie);

                    AuthorizationContextHolder.setContext(new AuthorizationContext(session, Collections.emptyList()));
                }
            }
        } catch (IllegalArgumentException e) {

        }
    }
}
