package com.example.handler;

import com.example.http.HttpRequest;
import com.example.http.HttpResponse;
import com.example.http.HttpStatus;
import com.example.processor.ArgumentResolver;
import com.example.processor.Triggerable;

public class ApiRequestHandlerAdapter<T, R> implements HttpHandlerAdapter<T, R> {

    private final ArgumentResolver<T> argumentResolver;

    public ApiRequestHandlerAdapter(ArgumentResolver<T> argumentResolver) {
        this.argumentResolver = argumentResolver;
    }

    @Override
    public void handle(HttpRequest request, HttpResponse response, Triggerable<T, R> triggerable) throws Exception {
        T resolve = argumentResolver.resolve(request);

        R run = triggerable.run(resolve);

        response.setStatus(HttpStatus.FOUND);

        response.getHttpHeaders().addHeader("Location", "/login");
    }
}
