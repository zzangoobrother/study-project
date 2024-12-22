package com.example.webserver.middleware;

import com.example.webserver.http.HttpRequest;
import com.example.webserver.http.HttpResponse;

public interface MiddleWare {

    void applyMiddleWare(HttpRequest request, HttpResponse response);
}
