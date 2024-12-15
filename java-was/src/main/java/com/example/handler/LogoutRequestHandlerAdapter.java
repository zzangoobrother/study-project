package com.example.handler;

import com.example.database.SessionDatabase;
import com.example.http.HttpRequest;
import com.example.http.HttpResponse;
import com.example.http.HttpStatus;
import com.example.http.header.HttpHeaders;

public class LogoutRequestHandlerAdapter extends ApiRequestHandlerAdapter<Void, Void> {
    @Override
    public Void resolveArgument(HttpRequest httpRequest) {
        return null;
    }

    @Override
    public void afterHandle(Void request, Void response, HttpRequest httpRequest, HttpResponse httpResponse) {
        HttpHeaders httpHeaders = httpRequest.getHttpHeaders();

        String cookie = httpHeaders.getHeader("Cookie");
        SessionDatabase.delete(cookie);
        httpResponse.setStatus(HttpStatus.OK);
    }

    @Override
    public void applyExceptionHandler(RuntimeException e, HttpResponse httpResponse) {
        httpResponse.setStatus(HttpStatus.BAD_REQUEST);
    }
}
