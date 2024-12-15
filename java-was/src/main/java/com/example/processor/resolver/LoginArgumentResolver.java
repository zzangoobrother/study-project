package com.example.processor.resolver;

import com.example.helper.RequestBodyParseHelper;
import com.example.http.HttpRequest;
import com.example.web.user.LoginRequest;

import java.util.Map;

public class LoginArgumentResolver implements ArgumentResolver<LoginRequest> {
    @Override
    public LoginRequest resolve(HttpRequest httpRequest) {
        String bodyString = httpRequest.getBody();

        Map<String, String> bodyParameters = RequestBodyParseHelper.bodyParameters(bodyString);

        String userId = bodyParameters.get("userId");
        String password = bodyParameters.get("password");

        if (userId == null || password == null) {
            throw new IllegalArgumentException("필수 파라미터가 누락되어있습니다.");
        }

        return new LoginRequest(userId, password);

    }
}
