package com.example.webserver.middleware;

import com.example.webserver.authorization.AuthorizationContext;
import com.example.webserver.authorization.AuthorizationContextHolder;
import com.example.webserver.http.HttpRequest;
import com.example.webserver.http.HttpResponse;
import com.example.webserver.http.Session;
import com.example.webserver.http.header.HeaderConstants;
import com.example.webserver.http.header.HttpHeaders;

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
