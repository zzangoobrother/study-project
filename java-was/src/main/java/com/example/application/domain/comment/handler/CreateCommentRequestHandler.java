package com.example.application.domain.comment.handler;

import com.example.api.Request;
import com.example.api.Response;
import com.example.application.domain.comment.request.CreateCommentRequest;
import com.example.application.handler.ApiRequestHandler;
import com.example.application.processor.ArgumentResolver;

import java.io.IOException;

public class CreateCommentRequestHandler extends ApiRequestHandler<CreateCommentRequest, Void> {

    private final ArgumentResolver<CreateCommentRequest> argumentResolver;

    public CreateCommentRequestHandler(ArgumentResolver<CreateCommentRequest> argumentResolver) {
        this.argumentResolver = argumentResolver;
    }

    @Override
    public CreateCommentRequest resolveArgument(Request httpRequest) {
        return argumentResolver.resolve(httpRequest);
    }

    @Override
    public void afterHandle(CreateCommentRequest request, Void response, Request httpRequest, Response httpResponse) {

    }

    @Override
    public void applyExceptionHandler(RuntimeException e, Response httpResponse) throws IOException {

    }
}
