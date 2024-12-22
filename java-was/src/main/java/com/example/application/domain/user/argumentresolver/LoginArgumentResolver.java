package com.example.application.domain.user.argumentresolver;

import com.example.application.processor.ArgumentResolver;
import com.example.webserver.helper.RequestBodyParseHelper;
import com.example.webserver.http.HttpRequest;
import com.example.application.domain.user.request.LoginRequest;

import java.util.Map;

public class LoginArgumentResolver implements ArgumentResolver<LoginRequest> {
    @Override
    public LoginRequest resolve(HttpRequest httpRequest) {
        String bodyString = httpRequest.getBody();

        Map<String, String> bodyParameters = RequestBodyParseHelper.urlEncodedParameters(bodyString);

        String userId = bodyParameters.get("userId");
        String password = bodyParameters.get("password");

        if (userId == null || password == null) {
            throw new IllegalArgumentException("필수 파라미터가 누락되어있습니다.");
        }

        return new LoginRequest(userId, password);

    }
}
