package com.example.processor;

import com.example.http.HttpRequest;
import com.example.http.Path;
import com.example.web.user.RegisterRequest;

import java.util.Map;

public class RegisterArgumentResolver implements ArgumentResolver<RegisterRequest> {
    @Override
    public RegisterRequest resolve(HttpRequest httpRequest) {
        Path path = httpRequest.getPath();
        Map<String, String> queryParameters = path.getQueryParameters();
        String email = queryParameters.get("email");
        String userId = queryParameters.get("userId");
        String password = queryParameters.get("password");
        String name = queryParameters.get("name");

        if (email == null || userId == null || password == null || name == null) {
            throw new IllegalArgumentException("필수 파라미터가 누락되었습니다.");
        }

        return new RegisterRequest(email, userId, password, name);
    }
}
