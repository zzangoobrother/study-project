package com.example.application.processor;

import com.example.webserver.http.HttpRequest;

public interface ArgumentResolver<T> {

    T resolve(HttpRequest httpRequest);
}
