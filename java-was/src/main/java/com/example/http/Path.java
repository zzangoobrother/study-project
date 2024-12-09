package com.example.http;

public class Path {
    private final String value;

    public Path(String value) {
        this.value = value;
    }

    public static Path of(String path) {
        return new Path(path);
    }

    public String getValue() {
        return value;
    }

    private String validatePath(String path) {
        if (path == null) {
            throw new IllegalArgumentException("Path cannot be null");
        }

        return path;
    }
}
