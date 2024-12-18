package com.example.authorization;

import com.example.http.HttpMethod;
import com.example.http.Path;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class SecurePathManager {

    private static final List<SecurePath> securePaths = new ArrayList<>();

    public static boolean isSecurePath(Path path, HttpMethod method) {
        return securePaths.stream()
                .filter(it -> it.getMethod() == method)
                .anyMatch(it -> it.getPattern().matcher(path.getBasePath()).matches());
    }

    public static void addSecurePath(String path, HttpMethod method) {
        securePaths.add(new SecurePath(path, method));
    }

    private static class SecurePath {
        private final Pattern pattern;
        private final HttpMethod method;

        public SecurePath(String path, HttpMethod method) {
            String regexPattern = path.replaceAll("\\{[^/]+\\}", "([^/]+)");
            this.pattern = Pattern.compile(regexPattern);
            this.method = method;
        }

        public Pattern getPattern() {
            return pattern;
        }

        public HttpMethod getMethod() {
            return method;
        }
    }
}
