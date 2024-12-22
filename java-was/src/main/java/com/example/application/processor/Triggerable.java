package com.example.application.processor;

public interface Triggerable<T, R> {

    R run(T t);
}
