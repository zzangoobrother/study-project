package com.example.handler;

import com.example.http.HttpRequest;
import com.example.http.HttpResponse;

public interface HttpHandler {
    void handle(HttpRequest httpRequest, HttpResponse response) throws Exception;
}
