package com.example.handler;

import com.example.http.HttpRequest;
import com.example.http.HttpResponse;
import com.example.processor.Triggerable;

public interface HttpHandler<T, R> {
    void handle(HttpRequest request, HttpResponse response, Triggerable<T, R> triggerable) throws Exception;
}
