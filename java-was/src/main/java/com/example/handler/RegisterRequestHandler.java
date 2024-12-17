package com.example.handler;

import com.example.http.HttpRequest;
import com.example.http.HttpResponse;
import com.example.http.HttpStatus;
import com.example.http.header.HeaderConstants;
import com.example.processor.resolver.ArgumentResolver;
import com.example.web.user.request.RegisterRequest;

public class RegisterRequestHandler extends ApiRequestHandler<RegisterRequest, Long> {

    private final ArgumentResolver<RegisterRequest> argumentResolver;

    public RegisterRequestHandler(ArgumentResolver<RegisterRequest> argumentResolver) {
        this.argumentResolver = argumentResolver;
    }

    @Override
    public RegisterRequest resolveArgument(HttpRequest httpRequest) {
        return argumentResolver.resolve(httpRequest);
    }

    @Override
    public void afterHandle(RegisterRequest request, Long response, HttpRequest httpRequest, HttpResponse httpResponse) {
        httpResponse.setStatus(HttpStatus.FOUND);
        httpResponse.setHttpHeaders(HeaderConstants.LOCATION, "/");
    }

    @Override
    public void applyExceptionHandler(RuntimeException e, HttpResponse httpResponse) {
        httpResponse.setStatus(HttpStatus.FOUND);
        httpResponse.setHttpHeaders(HeaderConstants.LOCATION, "/users/register_failed.html");
    }
}
