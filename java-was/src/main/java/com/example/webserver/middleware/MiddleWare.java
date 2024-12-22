package com.example.webserver.middleware;

import com.example.api.Request;
import com.example.api.Response;

public interface MiddleWare {

    void applyMiddleWare(Request request, Response response);
}
