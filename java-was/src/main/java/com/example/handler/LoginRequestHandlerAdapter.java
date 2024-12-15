package com.example.handler;

import com.example.http.HttpRequest;
import com.example.http.HttpResponse;
import com.example.model.User;
import com.example.processor.resolver.ArgumentResolver;
import com.example.web.user.LoginRequest;

public class LoginRequestHandlerAdapter extends ApiRequestHandlerAdapter<LoginRequest, User> {

    private final ArgumentResolver<LoginRequest> argumentResolver;

    public LoginRequestHandlerAdapter(ArgumentResolver<LoginRequest> argumentResolver) {
        this.argumentResolver = argumentResolver;
    }

    @Override
    public LoginRequest resolveArgument(HttpRequest httpRequest) {
        return argumentResolver.resolve(httpRequest);
    }

    @Override
    public void afterHandle(LoginRequest request, User response, HttpRequest httpRequest, HttpResponse httpResponse) {
        
    }

    @Override
    public void applyExceptionHandler(RuntimeException e, HttpResponse httpResponse) {

    }
}
