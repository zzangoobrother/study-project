package com.example.processor;

public interface Triggerable<T, R> {

    R run(T t);
}
