package com.example.application.domain.user.handler;

import com.example.api.Request;
import com.example.api.Response;
import com.example.application.domain.user.request.RegisterRequest;
import com.example.application.handler.ApiRequestHandler;
import com.example.application.processor.ArgumentResolver;
import com.example.webserver.http.HttpStatus;

import java.io.IOException;

public class RegisterRequestHandler extends ApiRequestHandler<RegisterRequest, Long> {

    private final ArgumentResolver<RegisterRequest> argumentResolver;

    public RegisterRequestHandler(ArgumentResolver<RegisterRequest> argumentResolver) {
        this.argumentResolver = argumentResolver;
    }

    @Override
    public RegisterRequest resolveArgument(Request httpRequest) {
        return argumentResolver.resolve(httpRequest);
    }

    @Override
    public void afterHandle(RegisterRequest request, Long response, Request httpRequest, Response httpResponse) {
        httpResponse.setStatus(HttpStatus.OK);
    }

    @Override
    public void applyExceptionHandler(RuntimeException e, Response httpResponse) throws IOException {
        if (e instanceof IllegalArgumentException) {
            httpResponse.setStatus(HttpStatus.BAD_REQUEST);
            httpResponse.getBody().write(e.getMessage().getBytes());
        }
    }
}
