package com.example.processor;

import com.example.http.HttpRequest;
import com.example.http.Path;
import com.example.web.user.RegisterRequest;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class RegisterArgumentResolver implements ArgumentResolver<RegisterRequest> {
    @Override
    public RegisterRequest resolve(HttpRequest httpRequest) {
        Map<String, String> queryParameters = bodyParameters(httpRequest.getBody());

        String email = queryParameters.get("email");
        String userId = queryParameters.get("userId");
        String password = queryParameters.get("password");
        String name = queryParameters.get("name");

        if (email == null || userId == null || password == null || name == null) {
            throw new IllegalArgumentException("필수 파라미터가 누락되었습니다.");
        }

        return new RegisterRequest(email, userId, password, name);
    }

    private Map<String, String> bodyParameters(String bodyString) {
        return Arrays.stream(bodyString.split("&"))
                .map(it -> {
                    String[] split = it.split("=");
                    String key = split[0];
                    String value = split.length > 1 ? split[1] : "";

                    return Map.entry(key, value);
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
