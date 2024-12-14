package com.example.processor;

import com.example.http.HttpRequest;

public interface ArgumentResolver<T> {

    T resolve(HttpRequest httpRequest);
}
