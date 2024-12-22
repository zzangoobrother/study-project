package com.example.application.handler;

import com.example.api.Request;
import com.example.api.Response;
import com.example.application.processor.Triggerable;

import java.io.IOException;

public interface HttpHandler<T, R> {
    void handle(Request request, Response response, Triggerable<T, R> triggerable) throws IOException;
}
