package com.example.application.processor;

import com.example.api.Request;

public interface ArgumentResolver<T> {

    T resolve(Request httpRequest);
}
