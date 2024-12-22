package com.example.application.domain.comment.handler;

import com.example.application.domain.comment.request.CreateCommentRequest;
import com.example.application.handler.ApiRequestHandler;
import com.example.webserver.http.HttpRequest;
import com.example.webserver.http.HttpResponse;
import com.example.application.processor.ArgumentResolver;

import java.io.IOException;

public class CreateCommentRequestHandler extends ApiRequestHandler<CreateCommentRequest, Void> {

    private final ArgumentResolver<CreateCommentRequest> argumentResolver;

    public CreateCommentRequestHandler(ArgumentResolver<CreateCommentRequest> argumentResolver) {
        this.argumentResolver = argumentResolver;
    }

    @Override
    public CreateCommentRequest resolveArgument(HttpRequest httpRequest) {
        return argumentResolver.resolve(httpRequest);
    }

    @Override
    public void afterHandle(CreateCommentRequest request, Void response, HttpRequest httpRequest, HttpResponse httpResponse) {

    }

    @Override
    public void applyExceptionHandler(RuntimeException e, HttpResponse httpResponse) throws IOException {

    }
}
