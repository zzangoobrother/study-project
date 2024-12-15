package com.example.processor.resolver;

import com.example.http.HttpRequest;

public interface ArgumentResolver<T> {

    T resolve(HttpRequest httpRequest);
}
