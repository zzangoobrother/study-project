package com.example.application.domain.user.argumentresolver;

import com.example.application.processor.ArgumentResolver;
import com.example.webserver.helper.RequestBodyParseHelper;
import com.example.webserver.http.HttpRequest;
import com.example.application.domain.user.request.RegisterRequest;

import java.util.Map;

public class RegisterArgumentResolver implements ArgumentResolver<RegisterRequest> {
    @Override
    public RegisterRequest resolve(HttpRequest httpRequest) {
        Map<String, String> queryParameters = RequestBodyParseHelper.urlEncodedParameters(httpRequest.getBody());

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
