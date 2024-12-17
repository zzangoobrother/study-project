package com.example.middleware;

import com.example.http.HttpRequest;
import com.example.http.HttpResponse;

public interface MiddleWare {

    void applyMiddleWare(HttpRequest request, HttpResponse response);
}
