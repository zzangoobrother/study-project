package com.example.webserver.http;

public enum HttpVersion {
    HTTP_1_1("HTTP/1.1");

    private final String version;

    HttpVersion(String version) {
        this.version = version;
    }

    public static HttpVersion of(String version) {
        if (version == null) {
            throw new IllegalArgumentException("HTTP Version cannot be null");
        }

        for (HttpVersion httpVersion : values()) {
            if (httpVersion.version.equalsIgnoreCase(version)) {
                return httpVersion;
            }
        }

        throw new IllegalArgumentException("Invalid HTTP Version : " + version);
    }

    public String getVersion() {
        return version;
    }
}
