package com.practice.domain;

public class ThreadLocalTest {

    public static final ThreadLocal<String> threadlocal = new ThreadLocal<>();

    public static void setThreadlocal(String value) {
        threadlocal.set(value);
    }

    public static String getThreadLocal() {
        return threadlocal.get();
    }
}
